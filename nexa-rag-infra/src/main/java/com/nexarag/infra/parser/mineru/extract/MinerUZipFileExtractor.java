package com.nexarag.infra.parser.mineru.extract;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.parser.model.ExtractedAssetBO;
import com.nexarag.infra.parser.model.ExtractedDocumentBO;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import com.nexarag.infra.parser.workspace.BoundedFileTransfer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU ZIP 文件化提取器，逐条目解压 Markdown 和图片到任务工作区，避免将 ZIP 内容整体读入内存。
 */
@Component
@RequiredArgsConstructor
public class MinerUZipFileExtractor {

    private final BoundedFileTransfer boundedFileTransfer;

    /**
     * 将 MinerU ZIP 响应解压为工作区内的 Markdown 文件和资源文件。
     *
     * @param zipInputStream MinerU 返回的 ZIP 输入流
     * @param workspace 当前任务工作区
     * @param maxExtractedBytes ZIP 解压后允许写入的最大字节数
     * @return 文件化的解析结果
     */
    public ExtractedDocumentBO extract(InputStream zipInputStream, ArtifactWorkspace workspace, long maxExtractedBytes) {
        // 1. 校验输入与解压大小边界。
        if (zipInputStream == null || workspace == null) {
            throw new ServiceException("MinerU ZIP 输入流和工作区不能为空");
        }
        if (maxExtractedBytes <= 0) {
            throw new ServiceException("MinerU ZIP 解压大小限制必须大于零");
        }

        // 2. 逐条目流式解压，并记录正文候选文件与图片文件。
        List<MarkdownCandidate> markdownCandidates = new ArrayList<>();
        List<ExtractedAssetBO> assets = new ArrayList<>();
        long extractedBytes = 0L;
        int entryCount = 0;
        try (ZipInputStream inputStream = new ZipInputStream(zipInputStream)) {
            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entryCount++;
                String safeName = normalizeAndValidateEntryName(entry.getName());
                long remainingBytes = maxExtractedBytes - extractedBytes;
                if (remainingBytes <= 0) {
                    throw new ServiceException("MinerU ZIP 解压内容超过大小限制");
                }
                if (isMarkdown(safeName)) {
                    Path markdownPath = workspace.resolve("mineru/" + safeName);
                    extractedBytes += copyEntry(inputStream, markdownPath, remainingBytes);
                    markdownCandidates.add(new MarkdownCandidate(safeName, markdownPath));
                } else if (isImage(safeName)) {
                    String relativePath = toRelativeAssetPath(safeName);
                    Path assetPath = workspace.resolve("assets/" + relativePath);
                    extractedBytes += copyEntry(inputStream, assetPath, remainingBytes);
                    assets.add(new ExtractedAssetBO(assetPath, relativePath, resolveContentType(assetPath)));
                }
                inputStream.closeEntry();
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ServiceException("读取 MinerU ZIP 产物失败", exception, BaseErrorCode.SERVICE_ERROR);
        }

        // 3. 选择优先级最高的 Markdown 主文件。
        MarkdownCandidate markdownCandidate = markdownCandidates.stream()
                .min(Comparator.comparingInt(candidate -> markdownPriority(candidate.fileName())))
                .orElseThrow(() -> new ServiceException("MinerU ZIP 产物中未找到 Markdown 文件"));
        return new ExtractedDocumentBO(markdownCandidate.path(), List.copyOf(assets),
                Map.of("parser", "mineru", "entryCount", entryCount, "assetCount", assets.size()));
    }

    /**
     * 创建父目录后复制当前 ZIP 条目。
     */
    private long copyEntry(InputStream inputStream, Path targetPath, long maxBytes) throws IOException {
        Files.createDirectories(targetPath.getParent());
        return boundedFileTransfer.copy(inputStream, targetPath, maxBytes);
    }

    /**
     * 拒绝 ZIP Slip 和绝对路径。
     */
    private String normalizeAndValidateEntryName(String entryName) {
        String normalized = entryName == null ? "" : entryName.replace('\\', '/');
        if (!StringUtils.hasText(normalized) || normalized.startsWith("/") || normalized.contains("../")
                || normalized.startsWith("..") || normalized.matches("^[A-Za-z]:.*")) {
            throw new ServiceException("MinerU ZIP 产物包含非法路径，entryName=" + entryName);
        }
        return normalized;
    }

    private boolean isMarkdown(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".md");
    }

    private boolean isImage(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".svg");
    }

    private String toRelativeAssetPath(String fileName) {
        int imagesIndex = fileName.indexOf("images/");
        return imagesIndex >= 0 ? fileName.substring(imagesIndex) : simpleName(fileName);
    }

    private String resolveContentType(Path assetPath) {
        String lower = assetPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "image/png";
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

    /**
     * Markdown 主文件候选项。
     */
    private record MarkdownCandidate(String fileName, Path path) {
    }
}
