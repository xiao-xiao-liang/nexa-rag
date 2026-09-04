# 认证身份 BloomFilter 重建操作指南

本文说明 `auth_user.email` 与 `auth_external_identity` 的 Redisson BloomFilter 如何在业务低峰期重建。BloomFilter 仅用于“必定不存在”的读优化；邮箱与第三方身份的唯一性、归属和认证结果始终由 MySQL 精确查询及唯一索引裁决。

## 1. 重建前提

重建必须由单个应用实例在业务低峰执行。认证服务不提供 BloomFilter 启停开关：是否使用当前活动版本只由 Redis 就绪标记 `nexa:auth:bloom:ready:v{n}=READY` 决定。标记不存在、读取失败或 Redis 故障时，认证请求自动绕过 BloomFilter 并查询数据库。

重建服务会在回填前删除当前活动版本和目标版本的就绪标记，使全部在线认证请求在重建期间回退数据库；因此不要手工创建该标记，也不要在回填完成前将活动版本切换到目标版本。

## 2. 执行单次重建

在低峰启动一个一次性应用实例，并额外传入新的版本号。例如将活动版本从 `v1` 重建到 `v2`：

```text
--nexa.auth.identity-bloom.rebuild-version=2
```

应用启动时，`AuthIdentityBloomRebuildRunner` 会：

1. 删除当前活动版本与 `v2` 的就绪标记，再删除 `v2` 的三个过滤器；
2. 以主键游标分页扫描 `auth_user.email` 和 `auth_external_identity`；
3. 回填 `userId|email`、`userId|providerCode`、`providerCode|providerSubject` 的 SHA-256 元素；
4. 全部成功后写入 Redis 键 `nexa:auth:bloom:ready:v2=READY`。

任一失败都不会写入就绪标记，线上认证会继续回退数据库。重建实例结束后必须移除 `rebuild-version`，避免后续启动重复清空目标版本。

## 3. 切换活动版本

确认重建日志包含“认证 BloomFilter 重建完成”，并抽样检查三个 `v2` 过滤器后，将 `AuthIdentityBloomConfiguration.ACTIVE_VERSION` 从 `1` 改为 `2`，再滚动发布所有应用实例。版本、容量和假阳性率均为代码常量，不在配置文件中提供开关或可变参数。

Redis 就绪标记或任一活动过滤器缺失、Redis 发生故障时，代码都会返回 `UNKNOWN` 并查询数据库，不会初始化空过滤器。旧版本过滤器不在解绑、换绑时删除元素；可待下一次低峰重建后按运维策略清理。

## 4. 回退

若发现重建或切换异常，删除活动版本的 Redis 就绪标记并确认认证请求已回退数据库；随后排查并重新执行低峰重建。此操作不会影响数据库中的邮箱、第三方绑定或验证码数据，只会停用读优化。
