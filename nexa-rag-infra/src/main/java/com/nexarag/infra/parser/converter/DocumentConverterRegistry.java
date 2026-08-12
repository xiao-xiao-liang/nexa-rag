package com.nexarag.infra.parser.converter;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.parser.model.DocumentFormat;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档转换器注册表，在应用启动阶段建立文件格式到唯一转换器的映射。
 */
@Component
public class DocumentConverterRegistry {

    private final Map<DocumentFormat, DocumentConverter> converters;

    /**
     * 根据 Spring 注入的转换器集合创建注册表。
     *
     * @param documentConverters 全部转换器
     */
    public DocumentConverterRegistry(List<DocumentConverter> documentConverters) {
        this.converters = Map.copyOf(buildConverters(documentConverters));
    }

    /**
     * 获取指定格式的唯一转换器。
     *
     * @param format 文档格式
     * @return 已注册的转换器
     */
    public DocumentConverter requiredConverter(DocumentFormat format) {
        if (format == null) {
            throw new ServiceException("文档格式不能为空");
        }
        DocumentConverter converter = converters.get(format);
        if (converter == null) {
            throw new ServiceException("未找到文档转换器，format=" + format);
        }
        return converter;
    }

    private Map<DocumentFormat, DocumentConverter> buildConverters(List<DocumentConverter> documentConverters) {
        Map<DocumentFormat, DocumentConverter> converterMap = new EnumMap<>(DocumentFormat.class);
        if (documentConverters == null) {
            return converterMap;
        }
        for (DocumentConverter converter : documentConverters) {
            registerConverter(converterMap, converter);
        }
        return converterMap;
    }

    private void registerConverter(Map<DocumentFormat, DocumentConverter> converterMap, DocumentConverter converter) {
        if (converter == null) {
            throw new ServiceException("文档转换器不能为空");
        }
        Set<DocumentFormat> supportedFormats = converter.supportedFormats();
        if (supportedFormats == null || supportedFormats.isEmpty()) {
            throw new ServiceException("文档转换器未声明支持格式，converter=" + converter.getClass().getName());
        }
        for (DocumentFormat format : supportedFormats) {
            if (format == null) {
                throw new ServiceException("文档转换器声明了空文件格式，converter=" + converter.getClass().getName());
            }
            if (converterMap.putIfAbsent(format, converter) != null) {
                throw new ServiceException("重复注册文档转换器，format=" + format);
            }
        }
    }
}
