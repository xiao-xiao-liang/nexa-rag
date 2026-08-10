# multipart 请求头修复设计

## 目标

修复文档上传请求被错误标记为 `application/json`，使后端能够接收 `multipart/form-data`。

## 设计

在前端通用 `request` 函数中：

- 请求体为 `FormData` 时，移除所有显式 `Content-Type`，由浏览器生成包含 boundary 的 multipart 请求头。
- 其他带请求体且调用方未显式传入 `Content-Type` 的请求，继续默认使用 `application/json`。
- 非 FormData 请求中，调用方显式提供的 `Content-Type` 始终保持不变。

## 验证

为通用请求封装增加回归测试，覆盖 FormData 不附加 JSON 请求头；既有文档上传接口测试继续校验文件和 JSON request part。
