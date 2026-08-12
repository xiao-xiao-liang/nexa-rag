package com.nexarag.infra.parser.mineru.client;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 关闭时删除临时文件的输入流，用于将远端 ZIP 响应以文件形式交给后续提取器。
 */
final class DeleteOnCloseFileInputStream extends FilterInputStream {

    private final Path temporaryFile;

    DeleteOnCloseFileInputStream(Path temporaryFile) throws IOException {
        super(Files.newInputStream(temporaryFile));
        this.temporaryFile = temporaryFile;
    }

    @Override
    public void close() throws IOException {
        IOException closeException = null;
        try {
            super.close();
        } catch (IOException exception) {
            closeException = exception;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException exception) {
            if (closeException != null) {
                closeException.addSuppressed(exception);
            } else {
                closeException = exception;
            }
        }
        if (closeException != null) {
            throw closeException;
        }
    }
}
