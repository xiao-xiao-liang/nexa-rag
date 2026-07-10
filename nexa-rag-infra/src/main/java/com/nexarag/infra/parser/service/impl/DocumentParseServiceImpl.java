package com.nexarag.infra.parser.service.impl;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.parser.model.DocumentParseRequest;
import com.nexarag.infra.parser.model.DocumentParseResult;
import com.nexarag.infra.parser.DocumentParser;
import com.nexarag.infra.parser.service.DocumentParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 文档解析服务实现，按文件类型选择可用解析器并执行解析。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParseServiceImpl implements DocumentParseService {

    private final List<DocumentParser> documentParsers;

    /**
     * 解析文档并返回解析产物。
     *
     * @param request 文档解析请求
     * @return 文档解析结果
     */
    @Override
    public DocumentParseResult parse(DocumentParseRequest request) {
        // 1. 校验解析请求
        validateRequest(request);

        // 2. 根据文件类型选择解析器
        DocumentParser parser = documentParsers.stream()
                .filter(documentParser -> documentParser.supports(request))
                .findFirst()
                .orElseThrow(() -> new ServiceException("未找到可用文档解析器，documentId="
                        + request.documentId() + "，fileType=" + request.fileType()));

        // 3. 执行解析并返回产物信息
        log.info("开始执行文档解析，documentId={}，fileType={}，parserClass={}",
                request.documentId(), request.fileType(), parser.getClass().getSimpleName());
        return parser.parse(request);
    }

    private void validateRequest(DocumentParseRequest request) {
        if (request == null) {
            throw new ServiceException("文档解析请求不能为空");
        }
        if (request.documentId() == null) {
            throw new ServiceException("文档ID不能为空");
        }
        if (!StringUtils.hasText(request.fileType())) {
            throw new ServiceException("文件类型不能为空，documentId=" + request.documentId());
        }
        if (!StringUtils.hasText(request.originalObjectName())) {
            throw new ServiceException("原始文件对象名不能为空，documentId=" + request.documentId());
        }
    }
}
