# 告警 HTML 邮件模板与真实发送测试接口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 发送结构化 HTML 邮件告警，并在告警总开关开启时提供按渠道触发真实告警的本地测试接口。

**Architecture:** `EmailAlertHtmlTemplate` 只渲染并转义 HTML，`EmailAlertChannelSender` 只负责 MIME 邮件投递。`AlertTestService` 构造不关联真实 Outbox 的测试消息并经 `AlertDispatcher` 路由，`AlertTestController` 仅暴露 HTTP 入口且由 `nexa.alert.enabled` 控制注册。

**Tech Stack:** Java 21、Spring Boot、Spring Mail、Jakarta Mail、Spring MVC、JUnit 5、Mockito、AssertJ。

---

### Task 1: HTML 模板渲染

**Files:**
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/channel/EmailAlertHtmlTemplate.java`
- Create: `nexa-rag-infra/src/test/java/com/nexarag/infra/alert/channel/EmailAlertHtmlTemplateTest.java`

- [ ] **Step 1: 写入失败测试**

```java
@Test
void shouldRenderEscapedHtmlForErrorAlert() {
    String html = new EmailAlertHtmlTemplate().render(message("索引<失败>&重试"));

    assertThat(html).contains("NexaRAG 任务失败告警")
            .contains("#D92D20")
            .contains("索引&lt;失败&gt;&amp;重试")
            .contains("文档 ID").contains("2026-08-07 18:00:00");
}
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -pl nexa-rag-infra -Dtest=EmailAlertHtmlTemplateTest test`

Expected: 编译失败，提示 `EmailAlertHtmlTemplate` 不存在。

- [ ] **Step 3: 实现最小模板渲染器**

```java
public final class EmailAlertHtmlTemplate {

    public String render(AlertMessage message) {
        String accentColor = message.severity() == AlertSeverity.ERROR ? "#D92D20" : "#F79009";
        return """
                <!doctype html><html lang=\"zh-CN\"><body style=\"margin:0;background:#f5f7fa;font-family:Arial,sans-serif;color:#1d2939;\">
                <div style=\"max-width:680px;margin:24px auto;background:#ffffff;border-radius:12px;overflow:hidden;\">
                <div style=\"padding:24px;background:%s;color:#ffffff;font-size:20px;font-weight:700;\">NexaRAG 任务失败告警</div>
                <table style=\"width:100%%;border-collapse:collapse;\"><tr><td style=\"padding:16px;\">失败原因</td><td style=\"padding:16px;\">%s</td></tr></table>
                </div></body></html>
                """.formatted(accentColor, HtmlUtils.htmlEscape(message.failureReason()));
    }
}
```

模板使用 `HtmlUtils.htmlEscape` 转义所有动态字段，使用 `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")` 格式化时间，并对 WARNING 与 ERROR 使用不同色带。

- [ ] **Step 4: 运行通过测试**

Run: `mvn -pl nexa-rag-infra -Dtest=EmailAlertHtmlTemplateTest test`

Expected: PASS。

### Task 2: MIME HTML 邮件投递

**Files:**
- Modify: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/channel/EmailAlertChannelSender.java`
- Modify: `nexa-rag-infra/src/test/java/com/nexarag/infra/alert/channel/EmailAlertChannelSenderTest.java`

- [ ] **Step 1: 写入失败测试**

```java
@Test
void shouldSendHtmlMimeMessage() throws Exception {
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

    sender.send(message());

    verify(mailSender).send(mimeMessage);
    assertThat(mimeMessage.getSubject()).contains("任务最终失败");
    assertThat(mimeMessage.getContentType()).contains("text/html");
}
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -pl nexa-rag-infra -Dtest=EmailAlertChannelSenderTest test`

Expected: 失败，旧实现未调用 `createMimeMessage()`。

- [ ] **Step 3: 将发送器改为 MIME 邮件**

```java
MimeMessage mimeMessage = mailSender.createMimeMessage();
MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
helper.setFrom(email.getFrom().trim());
helper.setTo(recipients);
helper.setSubject(template.subject(message));
helper.setText(template.render(message), true);
mailSender.send(mimeMessage);
```

保留既有渠道与收发件配置校验、`ServiceException` 包装和脱敏约束；删除 `SimpleMailMessage` 与纯文本格式化方法。

- [ ] **Step 4: 运行通过测试**

Run: `mvn -pl nexa-rag-infra -Dtest=EmailAlertChannelSenderTest test`

Expected: PASS。

### Task 3: 测试告警服务与接口

**Files:**
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/AlertTestService.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/alert/controller/AlertTestController.java`
- Create: `nexa-rag-infra/src/test/java/com/nexarag/infra/alert/AlertTestServiceTest.java`
- Create: `nexa-rag-infra/src/test/java/com/nexarag/infra/alert/controller/AlertTestControllerTest.java`
- Modify: `nexa-rag-boot/src/main/resources/application.yml`

- [ ] **Step 1: 写入服务失败测试**

```java
@Test
void shouldDispatchTestAlertToRequestedChannel() {
    service.send("email");

    ArgumentCaptor<AlertMessage> captor = ArgumentCaptor.forClass(AlertMessage.class);
    verify(dispatcher).dispatch(captor.capture());
    assertThat(captor.getValue().channel()).isEqualTo(AlertChannel.EMAIL);
    assertThat(captor.getValue().failureReason()).contains("测试");
}

@Test
void shouldRejectUnknownChannel() {
    assertThatThrownBy(() -> service.send("unknown"))
            .isInstanceOf(ServiceException.class).hasMessageContaining("不支持");
}
```

- [ ] **Step 2: 运行服务失败测试**

Run: `mvn -pl nexa-rag-infra -Dtest=AlertTestServiceTest test`

Expected: 编译失败，提示 `AlertTestService` 不存在。

- [ ] **Step 3: 实现服务与接口**

```java
@Service
@RequiredArgsConstructor
public class AlertTestService {
    public void send(String channelText) {
        AlertChannel channel = parseChannel(channelText);
        alertDispatcher.dispatch(buildTestMessage(channel));
    }
}

@RestController
@RequestMapping("/api/alert-tests")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.alert", name = "enabled", havingValue = "true")
public class AlertTestController {
    @PostMapping("/{channel}")
    public Result<Void> send(@PathVariable String channel) {
        alertTestService.send(channel);
        return Results.success();
    }
}
```

测试消息使用固定正数 ID、`WARNING` 级别和“测试告警”失败原因；控制器不接收正文参数，也不向响应暴露渠道配置。`application.yml` 的 `nexa.alert.enabled` 注释补充“开启后同时暴露本地测试接口”。

- [ ] **Step 4: 写入并运行控制器失败测试**

```java
@Test
void shouldDelegateRequestedChannel() {
    Result<Void> result = controller.send("feishu");

    verify(alertTestService).send("feishu");
    assertThat(result.code()).isEqualTo(Result.SUCCESS_CODE);
}
```

Run: `mvn -pl nexa-rag-infra -Dtest=AlertTestControllerTest test`

Expected: 编译失败，提示 `AlertTestController` 不存在。

- [ ] **Step 5: 运行服务与控制器通过测试**

Run: `mvn -pl nexa-rag-infra -Dtest=AlertTestServiceTest,AlertTestControllerTest test`

Expected: PASS。

### Task 4: 回归与真实渠道验证

**Files:**
- Modify: `docs/superpowers/specs/2026-08-07-alert-html-template-test-trigger-design.md`

- [ ] **Step 1: 运行基础设施告警测试集**

Run: `mvn -pl nexa-rag-infra -Dtest='AlertMessageTest,AlertDispatcherTest,FeishuAlertChannelSenderTest,EmailAlertChannelSenderTest,EmailAlertHtmlTemplateTest,AlertTestServiceTest,AlertTestControllerTest,RocketMqAlertConsumerTest,RocketMqAlertDeadLetterConsumerTest' test`

Expected: PASS。

- [ ] **Step 2: 编译启动模块**

Run: `mvn -pl nexa-rag-boot -am -DskipTests compile`

Expected: BUILD SUCCESS。

- [ ] **Step 3: 使用已配置的本地环境启动并触发真实渠道**

Run:

```powershell
Invoke-RestMethod -Method Post http://localhost:8009/api/alert-tests/feishu
Invoke-RestMethod -Method Post http://localhost:8009/api/alert-tests/email
```

Expected: 两次响应均为统一成功响应；人工确认飞书群出现测试文本、收件箱收到 HTML 测试邮件。

- [ ] **Step 4: 更新设计验证记录并检查差异**

Run: `git diff --check`

Expected: 无空白错误。
