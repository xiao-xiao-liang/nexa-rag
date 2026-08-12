package com.nexarag.infra.parser.pandoc;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.PandocProperties;
import com.nexarag.infra.messaging.document.DocumentPipelineNonRetryableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 受限执行 Pandoc 外部进程，持续排空标准错误流，避免子进程因管道写满而阻塞。
 */
@Component
@RequiredArgsConstructor
public class PandocProcessRunner {
    private final PandocProperties properties;

    /**
     * 执行 Pandoc 命令，并在失败时返回容量受限的标准错误诊断信息。
     *
     * @param command Pandoc 命令及参数
     * @param workDirectory 进程工作目录
     */
    public void run(List<String> command, Path workDirectory) {
        // 1. 启动进程，并将标准输出直接丢弃。
        try (ExecutorService executorService = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            Process process = new ProcessBuilder(command).directory(workDirectory.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();

            // 2. 进程执行期间并行排空标准错误流，避免管道写满导致死锁。
            Future<String> stderrFuture = executorService.submit(() -> drainAndCapture(process.getErrorStream()));
            boolean finished = process.waitFor(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
            }
            String stderr = awaitStderr(stderrFuture);

            // 3. 根据执行结果抛出可定位的异常。
            if (!finished) {
                throw new DocumentPipelineNonRetryableException("Pandoc执行超时");
            }
            if (process.exitValue() != 0) {
                throw new ServiceException("Pandoc执行失败，exitCode=" + process.exitValue() + "，stderr=" + stderr);
            }
        } catch (DocumentPipelineNonRetryableException | ServiceException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException("等待Pandoc执行结果时被中断", exception, BaseErrorCode.SERVICE_ERROR);
        } catch (Exception exception) {
            throw new ServiceException("启动Pandoc失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 持续读取标准错误流，仅保留允许用于诊断的前若干字节。
     */
    private String drainAndCapture(InputStream inputStream) throws IOException {
        int maxStderrBytes = properties.getMaxStderrBytes();
        if (maxStderrBytes < 0) {
            throw new ServiceException("Pandoc标准错误保留大小不能小于零");
        }
        try (InputStream errorStream = inputStream;
             ByteArrayOutputStream captured = new ByteArrayOutputStream(Math.min(maxStderrBytes, 1024))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = errorStream.read(buffer)) != -1) {
                int remaining = maxStderrBytes - captured.size();
                if (remaining > 0) {
                    captured.write(buffer, 0, Math.min(bytesRead, remaining));
                }
            }
            return captured.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * 获取异步标准错误读取结果，保留底层异常以便排障。
     */
    private String awaitStderr(Future<String> stderrFuture) throws InterruptedException {
        try {
            return stderrFuture.get();
        } catch (ExecutionException exception) {
            throw new ServiceException("读取Pandoc标准错误失败", exception.getCause(), BaseErrorCode.SERVICE_ERROR);
        }
    }
}
