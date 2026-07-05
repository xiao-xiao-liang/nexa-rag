package com.nexarag.infra.parser.mineru;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU ZIP 产物提取器，负责安全提取 Markdown 主文件和图片资源。
 */
@Component
public class MinerUZipResultExtractor {

    /**
     * 从 ZIP 流中提取 Markdown 和资源文件。
     *
     * @param zipInputStream ZIP 输入流
     * @return MinerU 解压结果
     */
    public MinerUExtractedResult extract(InputStream zipInputStream) {
        if (zipInputStream == null) {
            throw new ServiceException("MinerU ZIP 输入流不能为空");
        }
        List<MarkdownCandidate> markdownCandidates = new ArrayList<>();
        List<MinerUAssetFile> assetFiles = new ArrayList<>();
        int entryCount = 0;
        try (ZipInputStream inputStream = new ZipInputStream(zipInputStream)) {
            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entryCount++;
                String safeName = normalizeAndValidateEntryName(entry.getName());
                byte[] content = readAllBytes(inputStream);
                if (safeName.toLowerCase(Locale.ROOT).endsWith(".md")) {
                    markdownCandidates.add(new MarkdownCandidate(safeName, new String(content, StandardCharsets.UTF_8)));
                } else if (isImage(safeName)) {
                    assetFiles.add(new MinerUAssetFile(toRelativeAssetPath(safeName), simpleName(safeName), content));
                }
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("读取 MinerU ZIP 产物失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
        if (markdownCandidates.isEmpty()) {
            throw new ServiceException("MinerU ZIP 产物中未找到 Markdown 文件");
        }
        MarkdownCandidate markdown = markdownCandidates.stream()
                .min(Comparator.comparingInt(candidate -> markdownPriority(candidate.fileName())))
                .orElseThrow();
        return MinerUExtractedResult.builder()
                .markdownFileName(simpleName(markdown.fileName()))
                .markdownContent(markdown.content())
                .assetFiles(assetFiles)
                .metadata(Map.of("entryCount", entryCount, "assetCount", assetFiles.size()))
                .build();
    }

    private String normalizeAndValidateEntryName(String entryName) {
        String normalized = entryName.replace('\\', '/');
        if (!StringUtils.hasText(normalized)
                || normalized.startsWith("/")
                || normalized.contains("..")
                || normalized.matches("^[A-Za-z]:.*")) {
            throw new ServiceException("MinerU ZIP 产物包含非法路径，entryName=" + entryName);
        }
        return normalized;
    }

    private byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        inputStream.transferTo(outputStream);
        return outputStream.toByteArray();
    }

    private boolean isImage(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".gif");
    }

    private String toRelativeAssetPath(String fileName) {
        int imagesIndex = fileName.indexOf("images/");
        if (imagesIndex >= 0) {
            return fileName.substring(imagesIndex);
        }
        return simpleName(fileName);
    }

    private String simpleName(String fileName) {
        int slashIndex = fileName.lastIndexOf('/');
        return slashIndex >= 0 ? fileName.substring(slashIndex + 1) : fileName;
    }

    private int markdownPriority(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.contains("content")) {
            return 0;
        }
        if (lower.contains("result") || lower.contains("main")) {
            return 1;
        }
        return 2;
    }

    private record MarkdownCandidate(String fileName, String content) {
    }
}