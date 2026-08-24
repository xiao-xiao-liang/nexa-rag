# ADR：Sa-Token 使用加固 HttpOnly Cookie 传输，并实施 CSRF 防护

## 状态

已接受。

## 背景

NexaRAG 的 React 前端需要向 Spring Boot API 携带 Sa-Token。将 Token 暴露给前端脚本并存入 Web Storage 会使任一同源 XSS 漏洞能够导出长期凭据；Cookie 能借助浏览器安全属性保护 Token 机密性，但会带来跨站请求伪造风险。

OWASP 明确建议不要将认证 Token、会话 ID 或刷新 Token 存入 `localStorage` 或 `sessionStorage`，优先使用 `HttpOnly; Secure; SameSite=Strict` Cookie；同时指出 SameSite 只能作为 CSRF 的纵深防御，不能替代 CSRF Token。 [OWASP 会话管理](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html) 与 [OWASP CSRF 防护](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)

## 决策

1. Sa-Token 仅通过会话 Cookie 传输；前端不得保存、读取或通过 `Authorization` 头转发登录 Token。
2. 生产环境会话 Cookie 使用 `__Host-` 前缀、`Secure`、`HttpOnly`、`SameSite=Strict`、`Path=/`，且不设置 `Domain` 属性；全站必须使用 HTTPS 并启用 HSTS。边缘 HTTPS 响应设置一年有效期的 HSTS，暂不使用 `includeSubDomains`。当前由 Nginx 终止 `xiaoxiaoliang.top` 的 TLS，证书申请与续期由 Nginx/部署工具负责。
3. 生产前端与 API 由同一 Nginx 源统一暴露，`/api/` 反向代理至本机 Spring Boot；不得开启带凭据的宽松 CORS。开发环境继续使用本地 Vite 同源代理。
4. 所有 `POST`、`PUT`、`PATCH`、`DELETE` 等状态变更接口必须要求前端在自定义 `X-CSRF-Token` 请求头携带与当前登录会话绑定的 CSRF 挑战，并由服务端校验。
5. 状态变更接口还必须校验 `Origin`，以 `Sec-Fetch-Site` 拒绝跨站请求；缺失 Fetch Metadata 的客户端以严格 Origin 校验作为后备。`SameSite` 不作为唯一防线。
6. 第三方 OAuth/扫码回调必须以短期、一次性且服务端保存的 `state` 关联登录或绑定流程，不能依赖跨站回调自动携带现有会话 Cookie。
7. 为保持浏览器配置文件级的设备会话归类，服务端另设非认证设备标识 Cookie `__Host-nexa-device-id`，同样使用 `Secure`、`HttpOnly`、`SameSite=Strict`、`Path=/` 且不设置 `Domain`。其有效期为一年并按有效业务活动滑动续期。
8. 设备标识 Cookie 不得作为登录、授权、二次验证或 CSRF 凭据；普通登出不清除该 Cookie，以便用户下次登录仍识别为同一浏览器配置文件。用户清除浏览器数据后服务端应生成新标识。
9. Spring Boot 必须启用受限的原生转发头处理，仅信任来自回环 Nginx 的 `X-Forwarded-For` 与 `X-Forwarded-Proto`，以正确生成 HTTPS 绝对 URL、识别真实客户端 IP；不得对非回环来源信任此类请求头。Spring Boot 默认在非云平台不启用转发头处理，应显式配置。 [Spring Boot 嵌入式 Web 服务器文档](https://docs.spring.io/spring-boot/how-to/webserver.html)

## 备选方案

### 在 localStorage 或 sessionStorage 保存 Token，再以 Authorization 头发送

拒绝。任何能在同源执行的脚本均可读取并导出 Token，扩大 XSS 后果。

### 仅设置 SameSite Cookie，不做 CSRF 校验

拒绝。SameSite 不是完整 CSRF 防线，且同站子域、浏览器差异与客户端侧 CSRF 都可能绕过单一防护。

### 使用 SameSite=None 并向任意来源开放带凭据 CORS

拒绝。跨站 Cookie 传递和宽松来源授权会显著扩大 CSRF 与跨源凭据风险。

### 把设备标识写入 localStorage 或作为可认证 Token 使用

拒绝。前端脚本可读存储会扩大 XSS 影响；设备标识仅用于会话归类，不能成为任何认证或授权依据。

## 后果

- 前端登录成功后无需接收 Token 字段；API 客户端需在同源请求中携带 Cookie，并从受控接口取得 CSRF Token 后放入自定义请求头。
- 后端需实现会话与设备标识 Cookie 写入、会话登出清除、CSRF 挑战签发/轮换/校验和 Origin/Fetch Metadata 过滤器，并对预检请求保留适当放行。
- Nginx 与 Spring Boot 的转发头信任边界必须联测：Nginx 覆盖而非追加 `X-Forwarded-For`，Spring Boot 只信任 `127.0.0.1`，并确认通过 `/api/` 时应用识别的协议为 HTTPS。
- OAuth 与扫码回调需要以服务端状态机恢复原始流程；回调 URL、允许来源和重定向目标必须配置白名单。
- 必须为 Cookie 属性、设备标识的一年滑动有效期与普通登出保留、CSRF 缺失/错误、跨站请求拒绝、回调 state 重放以及会话登出 Cookie 清理编写集成测试。
