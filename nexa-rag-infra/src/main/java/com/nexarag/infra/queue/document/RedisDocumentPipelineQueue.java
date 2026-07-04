package com.nexarag.infra.queue.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis 文档流水线队列，使用 Lua 脚本保证入队、取租约、确认和释放操作的原子性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDocumentPipelineQueue implements DocumentPipelineQueue {

    static final String ENQUEUE_SCRIPT = """
            local waitingKey = KEYS[1]
            local runningKey = KEYS[2]
            local sequenceKey = KEYS[3]
            local metaKey = KEYS[4]
            local documentId = ARGV[1]
            local enqueueTime = ARGV[2]

            local rank = redis.call('ZRANK', waitingKey, documentId)
            local waitingCount = redis.call('ZCARD', waitingKey)
            if rank then
                local score = redis.call('ZSCORE', waitingKey, documentId)
                return {'WAITING', tostring(rank + 1), tostring(waitingCount), tostring(score)}
            end

            local runningJson = redis.call('HGET', runningKey, documentId)
            if runningJson then
                return {'RUNNING', '', tostring(waitingCount), runningJson}
            end

            local sequence = redis.call('INCR', sequenceKey)
            redis.call('ZADD', waitingKey, sequence, documentId)
            redis.call('HSET', metaKey, 'enqueueSequence', sequence, 'enqueueTime', enqueueTime)
            local newRank = redis.call('ZRANK', waitingKey, documentId)
            local newWaitingCount = redis.call('ZCARD', waitingKey)
            return {'WAITING', tostring(newRank + 1), tostring(newWaitingCount), tostring(sequence)}
            """;

    static final String POLL_SCRIPT = """
            local waitingKey = KEYS[1]
            local runningKey = KEYS[2]
            local firstTask = redis.call('ZRANGE', waitingKey, 0, 0, 'WITHSCORES')
            if #firstTask == 0 then
                return nil
            end

            local documentId = firstTask[1]
            local enqueueSequence = firstTask[2]
            local workerId = ARGV[1]
            local leaseToken = ARGV[2]
            local leaseTtlMillis = ARGV[3]
            local startTime = ARGV[4]
            local leaseExpireTime = ARGV[5]
            local leaseKeyPrefix = ARGV[6]

            redis.call('ZREM', waitingKey, documentId)
            local runningJson = '{"documentId":' .. documentId
                    .. ',"leaseToken":"' .. leaseToken
                    .. '","workerId":"' .. workerId
                    .. '","startTime":"' .. startTime
                    .. '","leaseExpireTime":"' .. leaseExpireTime
                    .. '","enqueueSequence":' .. enqueueSequence .. '}'
            redis.call('HSET', runningKey, documentId, runningJson)
            redis.call('SET', leaseKeyPrefix .. documentId, leaseToken, 'PX', leaseTtlMillis)
            return {'TASK', documentId, tostring(enqueueSequence), leaseToken}
            """;

    static final String ACK_SCRIPT = """
            local runningKey = KEYS[1]
            local leaseKey = KEYS[2]
            local metaKey = KEYS[3]
            local documentId = ARGV[1]
            local leaseToken = ARGV[2]

            if redis.call('GET', leaseKey) == leaseToken then
                redis.call('HDEL', runningKey, documentId)
                redis.call('DEL', leaseKey)
                redis.call('DEL', metaKey)
                return 1
            end
            return 0
            """;

    static final String RELEASE_SCRIPT = """
            local waitingKey = KEYS[1]
            local runningKey = KEYS[2]
            local leaseKey = KEYS[3]
            local sequenceKey = KEYS[4]
            local metaKey = KEYS[5]
            local documentId = ARGV[1]
            local leaseToken = ARGV[2]
            local requeue = ARGV[3]
            local enqueueTime = ARGV[4]

            if redis.call('GET', leaseKey) ~= leaseToken then
                return -1
            end
            redis.call('HDEL', runningKey, documentId)
            redis.call('DEL', leaseKey)
            if requeue == 'true' then
                local sequence = redis.call('INCR', sequenceKey)
                redis.call('ZADD', waitingKey, sequence, documentId)
                redis.call('HSET', metaKey, 'enqueueSequence', sequence, 'enqueueTime', enqueueTime)
                return sequence
            end
            redis.call('DEL', metaKey)
            return 0
            """;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String STATUS_WAITING = "WAITING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String TASK = "TASK";
    private static final TypeReference<Map<String, Object>> RUNNING_STATE_TYPE_REFERENCE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final DocumentPipelineQueueKeys keys;

    /**
     * 将文档加入 Redis 等待队列。
     *
     * @param documentId 文档ID
     * @return 文档队列状态
     */
    @Override
    public DocumentPipelineQueueStatus enqueue(Long documentId) {
        validateDocumentId(documentId);
        List<Object> result = executeListScript(ENQUEUE_SCRIPT,
                List.of(keys.waitingKey(), keys.runningKey(), keys.sequenceKey(), keys.metaKey(documentId)),
                documentId.toString(), LocalDateTime.now().toString());
        return parseEnqueueResult(documentId, result);
    }

    /**
     * 从 Redis 等待队列获取最早入队的文档任务租约。
     *
     * @param workerId 工作器ID
     * @param leaseTtl 租约时长
     * @return 获取到的任务；队列为空时返回 Optional.empty()
     */
    @Override
    public Optional<DocumentPipelineTask> poll(String workerId, Duration leaseTtl) {
        if (workerId == null || workerId.isBlank()) {
            throw new ServiceException("Worker ID 不能为空");
        }
        if (leaseTtl == null || leaseTtl.isNegative() || leaseTtl.isZero()) {
            throw new ServiceException("任务租约时长必须大于0");
        }

        // 1. 生成租约令牌和时间边界，由 Lua 原子移动 waiting 到 running
        String leaseToken = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();
        List<Object> result = executeListScript(POLL_SCRIPT,
                List.of(keys.waitingKey(), keys.runningKey()),
                workerId, leaseToken, String.valueOf(leaseTtl.toMillis()), startTime.toString(),
                startTime.plus(leaseTtl).toString(), keys.leaseKeyPrefix());
        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }
        if (!TASK.equals(toString(result.getFirst()))) {
            throw new ServiceException("Redis 返回的文档流水线任务格式不合法");
        }
        return Optional.of(new DocumentPipelineTask(toLong(result.get(1)), toString(result.get(3)),
                workerId, toLong(result.get(2))));
    }

    /**
     * 确认 Redis 运行中文档任务完成。
     *
     * @param documentId 文档ID
     * @param leaseToken 租约令牌
     */
    @Override
    public void ack(Long documentId, String leaseToken) {
        validateDocumentId(documentId);
        if (leaseToken == null || leaseToken.isBlank()) {
            throw new ServiceException("租约令牌不能为空");
        }
        executeLongScript(ACK_SCRIPT, List.of(keys.runningKey(), keys.leaseKey(documentId), keys.metaKey(documentId)),
                documentId.toString(), leaseToken);
    }

    /**
     * 释放 Redis 运行中文档任务。
     *
     * @param documentId 文档ID
     * @param leaseToken 租约令牌
     * @param requeue    是否重新入队
     */
    @Override
    public void release(Long documentId, String leaseToken, boolean requeue) {
        validateDocumentId(documentId);
        if (leaseToken == null || leaseToken.isBlank()) {
            throw new ServiceException("租约令牌不能为空");
        }
        executeLongScript(RELEASE_SCRIPT,
                List.of(keys.waitingKey(), keys.runningKey(), keys.leaseKey(documentId),
                        keys.sequenceKey(), keys.metaKey(documentId)),
                documentId.toString(), leaseToken, String.valueOf(requeue), LocalDateTime.now().toString());
    }

    /**
     * 查询 Redis 中的文档队列状态。
     *
     * @param documentId 文档ID
     * @return 队列状态；Redis 无状态时返回 Optional.empty()
     */
    @Override
    public Optional<DocumentPipelineQueueStatus> queryStatus(Long documentId) {
        validateDocumentId(documentId);

        // 1. 优先查询 waiting 队列位置
        Long rank = redisTemplate.opsForZSet().rank(keys.waitingKey(), documentId.toString());
        Long waitingCount = redisTemplate.opsForZSet().zCard(keys.waitingKey());
        if (rank != null) {
            Double score = redisTemplate.opsForZSet().score(keys.waitingKey(), documentId.toString());
            return Optional.of(new DocumentPipelineQueueStatus(documentId, Math.toIntExact(rank + 1),
                    safeInt(waitingCount), false, null, null, score == null ? null : score.longValue()));
        }

        // 2. 再查询 running 状态和租约剩余时间
        Object runningJson = redisTemplate.opsForHash().get(keys.runningKey(), documentId.toString());
        if (runningJson == null) {
            return Optional.empty();
        }
        RunningState runningState = parseRunningState(runningJson.toString());
        Long leaseTtlSeconds = redisTemplate.getExpire(keys.leaseKey(documentId));
        return Optional.of(new DocumentPipelineQueueStatus(documentId, null, safeInt(waitingCount), true,
                runningState.workerId(), leaseTtlSeconds, runningState.enqueueSequence()));
    }

    private DocumentPipelineQueueStatus parseEnqueueResult(Long documentId, List<Object> result) {
        if (result == null || result.isEmpty()) {
            throw new ServiceException("Redis 返回的文档入队结果为空");
        }
        String status = toString(result.getFirst());
        if (STATUS_WAITING.equals(status)) {
            return new DocumentPipelineQueueStatus(documentId, toInteger(result.get(1)), toInteger(result.get(2)),
                    false, null, null, toLong(result.get(3)));
        }
        if (STATUS_RUNNING.equals(status)) {
            RunningState runningState = parseRunningState(toString(result.get(3)));
            Long leaseTtlSeconds = redisTemplate.getExpire(keys.leaseKey(documentId));
            return new DocumentPipelineQueueStatus(documentId, null, toInteger(result.get(2)), true,
                    runningState.workerId(), leaseTtlSeconds, runningState.enqueueSequence());
        }
        throw new ServiceException("Redis 返回的文档入队状态不合法，status=" + status);
    }

    private List<Object> executeListScript(String script, List<String> keyList, String... args) {
        try {
            return redisTemplate.execute(new DefaultRedisScript<>(script, List.class), keyList, (Object[]) args);
        } catch (RuntimeException exception) {
            throw new ServiceException("执行 Redis 文档队列脚本失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private Long executeLongScript(String script, List<String> keyList, String... args) {
        try {
            return redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), keyList, (Object[]) args);
        } catch (RuntimeException exception) {
            throw new ServiceException("执行 Redis 文档队列脚本失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private RunningState parseRunningState(String runningJson) {
        try {
            Map<String, Object> runningMap = OBJECT_MAPPER.readValue(runningJson, RUNNING_STATE_TYPE_REFERENCE);
            return new RunningState(toString(runningMap.get("workerId")), toLong(runningMap.get("enqueueSequence")));
        } catch (JsonProcessingException exception) {
            throw new ServiceException("解析 Redis 文档运行态失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private void validateDocumentId(Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new ServiceException("文档ID必须大于0");
        }
    }

    private String toString(Object value) {
        return value == null ? null : value.toString();
    }

    private Long toLong(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }

    private Integer toInteger(Object value) {
        return value == null || value.toString().isBlank() ? null : Integer.valueOf(value.toString());
    }

    private Integer safeInt(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }

    private record RunningState(String workerId, Long enqueueSequence) {
    }
}
