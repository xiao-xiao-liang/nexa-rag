package com.nexarag.chat.domain;

/**
 * 经服务端归属校验后的引用预览内容。
 *
 * @param citationId 消息内引用编号
 * @param title 文档标题
 * @param chunkOrder 文档内分块顺序
 * @param content 当前分块正文
 * @param documentPath 站内文档详情路径
 * @param sourceUrl 已校验的外部来源地址，内部文档为空
 */
public record ChatCitationDetailVO(int citationId, String title, Integer chunkOrder, String content,
                                   String documentPath, String sourceUrl) {
}
