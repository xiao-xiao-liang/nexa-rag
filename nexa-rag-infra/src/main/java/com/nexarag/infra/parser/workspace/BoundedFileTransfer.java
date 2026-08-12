package com.nexarag.infra.parser.workspace;

import com.nexarag.infra.messaging.document.DocumentPipelineNonRetryableException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 有界文件复制工具，用于限制外部文件落盘大小。 */
@Component
public class BoundedFileTransfer {

    private static final int BUFFER_SIZE = 8192;

    /** 按实际读取字节数复制输入流。 */
    public long copy(InputStream source, Path target, long maxBytes) throws IOException {
        long copiedBytes = 0L;
        try (OutputStream outputStream = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int readSize;
            while ((readSize = source.read(buffer)) >= 0) {
                copiedBytes = Math.addExact(copiedBytes, readSize);
                if (copiedBytes > maxBytes) {
                    throw new DocumentPipelineNonRetryableException("文件超过大小限制，maxBytes=" + maxBytes);
                }
                outputStream.write(buffer, 0, readSize);
            }
            return copiedBytes;
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
    }
}
