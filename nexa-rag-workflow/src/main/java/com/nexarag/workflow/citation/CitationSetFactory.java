package com.nexarag.workflow.citation;

import com.nexarag.chat.domain.ChatCitationDTO;
import com.nexarag.retrieval.model.RetrievalChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将已接纳检索证据转换为消息内稳定编号的引用清单。
 */
@Component
public class CitationSetFactory {

    /**
     * 按已接纳证据的既有顺序生成引用，避免改变模型上下文顺序。
     *
     * @param acceptedChunks 已接纳证据
     * @return 不含正文和知识库 ID 的引用清单
     */
    public List<ChatCitationDTO> create(List<RetrievalChunk> acceptedChunks) {
        if (acceptedChunks == null || acceptedChunks.isEmpty()) {
            return List.of();
        }
        List<ChatCitationDTO> citations = new ArrayList<>(acceptedChunks.size());
        for (int index = 0; index < acceptedChunks.size(); index++) {
            RetrievalChunk chunk = acceptedChunks.get(index);
            citations.add(new ChatCitationDTO(index + 1, chunk.documentId(), chunk.chunkId(),
                    chunk.chunkIndex(), chunk.title(), null, chunk.rank(), chunk.score(), chunk.channel()));
        }
        return List.copyOf(citations);
    }
}
