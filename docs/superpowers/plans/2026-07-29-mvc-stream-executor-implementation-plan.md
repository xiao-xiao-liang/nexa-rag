# MVC 流式执行器实现计划

> **面向执行代理：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项执行。步骤使用复选框追踪。

**目标：** 在现有执行器配置中提供容量为 80 的 `applicationTaskExecutor`，供 Spring MVC 流式 SSE 自动使用。

**架构：** 仅扩展 `ExecutorConfiguration`，保留摘要任务专用的 `chatSummaryExecutor`。新 Bean 使用 Spring Boot 约定名称 `applicationTaskExecutor`；Web MVC 自动配置会发现并注入该 Bean，无需新增 `WebMvcConfigurer` 或修改控制器。

**技术栈：** Java 21、Spring Boot 3.5、Spring MVC、`ThreadPoolTaskExecutor`。

---

### 任务 1：注册 MVC 流式执行器

**文件：**

- 修改：`nexa-rag-chat/src/main/java/com/nexarag/chat/config/ExecutorConfiguration.java`
- 不新增测试：用户已明确要求不新增测试。

- [ ] **步骤 1：补充 Spring 线程池执行器导入**

在现有 `TaskExecutor` 与 `VirtualThreadTaskExecutor` 导入旁新增：

```java
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
```

- [ ] **步骤 2：在现有配置类中新增 MVC 执行器 Bean**

在 `chatSummaryExecutor()` 后新增以下方法，保留现有摘要执行器与信号量 Bean 不变：

```java
/**
 * 创建 Spring MVC 流式响应执行器。
 *
 * @return MVC 流式响应执行器
 */
@Bean(name = "applicationTaskExecutor")
public TaskExecutor applicationTaskExecutor() {
    // 1. 创建与摘要任务隔离的 MVC 流式响应线程池
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(80);
    executor.setMaxPoolSize(80);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("mvc-stream-");
    return executor;
}
```

`applicationTaskExecutor` 是 Spring Boot MVC 自动配置识别的 Bean 名称；80/80/0 分别对应单实例 80 条活跃流、最大并发不扩容和不进行服务端排队。

- [ ] **步骤 3：编译受影响模块**

运行：

```powershell
mvn -pl nexa-rag-chat -am -DskipTests compile
```

预期：命令以退出码 0 结束，`nexa-rag-chat` 及其依赖模块编译成功。

- [ ] **步骤 4：检查变更范围与格式**

运行：

```powershell
git diff --check -- nexa-rag-chat/src/main/java/com/nexarag/chat/config/ExecutorConfiguration.java
git diff -- nexa-rag-chat/src/main/java/com/nexarag/chat/config/ExecutorConfiguration.java
```

预期：无空白错误；差异仅包含 MVC 流式执行器 Bean 及其所需导入。
