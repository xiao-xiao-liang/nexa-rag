package com.nexarag.infra.source.feishu;

import java.nio.file.Path;

/**
 * 飞书 Block 媒体资源下载结果。
 *
 * @param blockId       来源 Block ID
 * @param blockType     来源 Block 类型
 * @param status        下载状态
 * @param relativePath  相对 Markdown 的资源路径
 * @param file          工作区资源文件，仅下载成功时存在
 * @param contentType   资源内容类型
 * @param size          已落盘资源字节数
 * @param failureReason 下载失败或跳过原因
 */
public record FeishuBlockMediaDownloadResultBO(String blockId, int blockType, Status status, String relativePath,
                                               Path file, String contentType, long size, String failureReason) {

    /** 媒体资源处理状态。 */
    public enum Status {
        /** 已成功写入工作区。 */
        DOWNLOADED,
        /** 未下载，例如无 token 或超过配置边界。 */
        SKIPPED,
        /** 下载请求或文件写入失败。 */
        FAILED
    }

    /** 创建下载成功结果。 */
    public static FeishuBlockMediaDownloadResultBO downloaded(String blockId, int blockType, String relativePath,
                                                               Path file, String contentType, long size) {
        return new FeishuBlockMediaDownloadResultBO(blockId, blockType, Status.DOWNLOADED, relativePath, file,
                contentType, size, null);
    }

    /** 创建下载失败或跳过结果。 */
    public static FeishuBlockMediaDownloadResultBO unavailable(String blockId, int blockType, Status status,
                                                                String failureReason) {
        return new FeishuBlockMediaDownloadResultBO(blockId, blockType, status, null, null, null, 0L,
                failureReason);
    }
}
