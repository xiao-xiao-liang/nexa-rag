package com.nexarag.infra.parser.workspace;

import com.nexarag.infra.messaging.document.DocumentPipelineNonRetryableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 有界文件复制工具测试。
 */
class BoundedFileTransferTest {

    @TempDir
    Path tempDir;

    @Test
    void copyShouldDeleteTargetWhenInputExceedsLimit() {
        BoundedFileTransfer transfer = new BoundedFileTransfer();
        Path target = tempDir.resolve("source.docx");

        assertThatThrownBy(() -> transfer.copy(new ByteArrayInputStream(new byte[11]), target, 10L))
                .isInstanceOf(DocumentPipelineNonRetryableException.class)
                .hasMessageContaining("超过大小限制");
        assertThat(Files.exists(target)).isFalse();
    }

    @Test
    void copyShouldNotCloseInputStreamOwnedByCaller() throws Exception {
        CloseTrackingInputStream source = new CloseTrackingInputStream(new ByteArrayInputStream("content".getBytes()));

        new BoundedFileTransfer().copy(source, tempDir.resolve("source.txt"), 10L);

        assertThat(source.closed).isFalse();
    }

    private static final class CloseTrackingInputStream extends FilterInputStream {

        private boolean closed;

        private CloseTrackingInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
