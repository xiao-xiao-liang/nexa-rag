package com.nexarag.infra.parser.workspace;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.ArtifactProcessingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 解析工作区测试。
 */
class ArtifactWorkspaceTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveShouldRejectPathOutsideWorkspace() throws Exception {
        try (ArtifactWorkspace workspace = createWorkspaceFactory().create(101L)) {
            assertThatThrownBy(() -> workspace.resolve("../escape.md"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("非法工作区路径");
        }
    }

    @Test
    void closeShouldDeleteWorkspaceDirectory() throws Exception {
        ArtifactWorkspace workspace = createWorkspaceFactory().create(101L);
        Path root = workspace.root();
        Files.writeString(workspace.resolve("content.md"), "# 标题");

        workspace.close();

        assertThat(Files.exists(root)).isFalse();
    }

    private ArtifactWorkspaceFactory createWorkspaceFactory() {
        ArtifactProcessingProperties properties = new ArtifactProcessingProperties();
        properties.setTempRoot(tempDir);
        return new ArtifactWorkspaceFactory(properties);
    }
}
