package com.nexarag.infra.enums;

/**
 * 文档内容的来源类型，用于在文档模块和基础设施模块之间路由读取方式。
 */
public enum ExternalDocumentSourceType {

    /** 本地上传文件。 */
    LOCAL,

    /** 飞书在线文档，Reader 内部识别 Docx 或 Wiki 节点。 */
    FEISHU,

    /** 语雀单篇文档。 */
    YUQUE
}
