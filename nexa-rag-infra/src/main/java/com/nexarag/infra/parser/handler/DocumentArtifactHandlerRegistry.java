package com.nexarag.infra.parser.handler;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.parser.model.DocumentFormat;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档制品处理策略注册表，保证每种格式在运行时仅对应一个处理策略。
 */
@Component
public class DocumentArtifactHandlerRegistry {

    private final Map<DocumentFormat, DocumentArtifactHandler> handlers;

    /**
     * 根据 Spring 注入的处理策略集合创建注册表。
     *
     * @param documentArtifactHandlers 全部文档制品处理策略
     */
    public DocumentArtifactHandlerRegistry(List<DocumentArtifactHandler> documentArtifactHandlers) {
        this.handlers = Map.copyOf(buildHandlers(documentArtifactHandlers));
    }

    /**
     * 获取指定格式的唯一处理策略。
     *
     * @param format 文档格式
     * @return 已注册处理策略
     */
    public DocumentArtifactHandler requiredHandler(DocumentFormat format) {
        if (format == null) {
            throw new ServiceException("文档格式不能为空");
        }
        DocumentArtifactHandler handler = handlers.get(format);
        if (handler == null) {
            throw new ServiceException("未找到文档制品处理器，format=" + format);
        }
        return handler;
    }

    private Map<DocumentFormat, DocumentArtifactHandler> buildHandlers(
            List<DocumentArtifactHandler> documentArtifactHandlers) {
        Map<DocumentFormat, DocumentArtifactHandler> handlerMap = new EnumMap<>(DocumentFormat.class);
        if (documentArtifactHandlers == null) {
            return handlerMap;
        }
        for (DocumentArtifactHandler handler : documentArtifactHandlers) {
            registerHandler(handlerMap, handler);
        }
        return handlerMap;
    }

    private void registerHandler(Map<DocumentFormat, DocumentArtifactHandler> handlerMap,
                                 DocumentArtifactHandler handler) {
        if (handler == null) {
            throw new ServiceException("文档制品处理器不能为空");
        }
        Set<DocumentFormat> supportedFormats = handler.supportedFormats();
        if (supportedFormats == null || supportedFormats.isEmpty()) {
            throw new ServiceException("文档制品处理器未声明支持格式，handler=" + handler.getClass().getName());
        }
        for (DocumentFormat format : supportedFormats) {
            if (format == null) {
                throw new ServiceException("文档制品处理器声明了空文件格式，handler=" + handler.getClass().getName());
            }
            if (handlerMap.putIfAbsent(format, handler) != null) {
                throw new ServiceException("重复注册文档制品处理器，format=" + format);
            }
        }
    }
}
