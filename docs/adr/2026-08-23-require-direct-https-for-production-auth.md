# ADR：生产认证流量由 Nginx 提供 HTTPS 并反向代理至 Spring Boot

## 状态

已接受，生产 HTTPS 已就绪。

## 背景

生产域名为 `xiaoxiaoliang.top`，认证方案依赖 `Secure`、`HttpOnly`、`SameSite=Strict` 与 `__Host-` Cookie；同时 Google 生产 OAuth 回调要求 HTTPS。Nginx 负责对外提供前端与 HTTPS，并将 `/api/` 反向代理到本机 Spring Boot 的 `127.0.0.1:8009`。公网 HTTP 会暴露密码、邮箱验证码、会话和第三方授权回调数据，不能作为认证模块的生产承载协议。

## 决策

1. 生产环境必须以 `https://xiaoxiaoliang.top` 对外提供前端、API 与四个第三方登录回调；未完成 HTTPS 前不得发布真实认证功能到公网 HTTP。
2. Nginx 在公网 443 端口终止 TLS，静态前端由 Nginx 提供，`/api/` 仅由 Nginx 反向代理至本机 `127.0.0.1:8009` 的 Spring Boot；Spring Boot 不直接暴露公网端口。
3. 证书使用受信任 CA 签发的证书，优先采用 Let’s Encrypt 等 ACME 方式由 Nginx/部署工具自动申请与续期。证书与私钥只从受限文件路径、环境变量或密钥管理注入，禁止提交仓库、写入日志或返回接口。
4. HTTP 80 端口只能用于 ACME HTTP-01 校验和重定向到 HTTPS；不得通过 HTTP 提供前端认证页面、`/api/` 接口或 OAuth 回调。
5. 上线前必须验证 `https://xiaoxiaoliang.top`、`/api` 路径和四个 OAuth 回调均可通过有效证书访问，并确认 HTTP 不承载认证请求或被安全重定向到 HTTPS。
6. 部署服务器可对公网开放 TCP 80 和 443，满足 HTTP-01 证书域名校验与 HTTPS 服务端口的基础网络条件。

## 备选方案

### 以公网 HTTP 发布认证模块

拒绝。HTTP 无法安全承载密码、验证码或登录会话；浏览器也不能在 HTTP 站点设置 `Secure` 或 `__Host-` Cookie。 [MDN Set-Cookie](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie)

### 为兼容 HTTP 去掉 Secure 与 __Host- Cookie

拒绝。会显著削弱会话机密性与会话固定防护，且不能解决密码、验证码和 OAuth 回调的明文传输问题。

### 延后 HTTPS 但先启用 Google 生产 OAuth

拒绝。Google 的 OAuth 2.0 政策要求 Web 应用的重定向 URI 与 JavaScript origin 使用符合校验规则的 HTTPS。 [Google OAuth 政策](https://developers.google.com/identity/protocols/oauth2/policies)

## 后果

- 部署前必须完成 DNS 指向、已确认可开放的 80/443 端口、ACME 域名校验、证书文件权限和自动续期任务配置。
- Nginx 的 HTTPS 反向代理必须传递并覆盖 `Host`、`X-Real-IP`、`X-Forwarded-For` 和 `X-Forwarded-Proto`；其中 `X-Forwarded-For` 在当前无上游代理/CDN 时必须覆盖为 `$remote_addr`，不得使用会保留客户端伪造值的 `$proxy_add_x_forwarded_for`。
- 已批准的 Nginx 变更为：将 `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;` 调整为 `proxy_set_header X-Forwarded-For $remote_addr;`，防止客户端预置伪造的转发地址影响 IP 限流、设备地区或审计。该变更需在部署服务器实际执行后才生效。
- 开发环境可继续使用 `localhost` 或受控 HTTPS 开发域名；本地 HTTP 不得被视为生产认证安全性的验证依据。
- TLS 证书状态、续期失败和 HTTPS 健康检查需纳入部署监控；证书配置完成前，真实 QQ、飞书、Google、GitHub 登录不具备生产上线条件。
