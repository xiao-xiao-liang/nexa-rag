package com.nexarag.chat.domain;

/**
 * 助手消息引用的内部定位数据，不保存证据正文或访问地址。
 *
 * @param citationId      消息内引用编号
 * @param documentId      文档 ID
 * @param chunkId         分块 ID
 * @param chunkOrder      文档内分块顺序
 * @param title           文档标题快照
 * @param sectionId       章节 ID
 * @param rank            已接纳证据中的排序
 * @param score           检索分数
 * @param channel         检索通道
 */
public record ChatCitationDTO(int citationId, Long documentId, String chunkId,
                              Integer chunkOrder, String title, Long sectionId, int rank,
                              double score, String channel) {
}
