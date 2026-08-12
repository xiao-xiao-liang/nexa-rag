package com.nexarag.infra.parser.workspace;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** 单次文档解析的受管临时工作区。 */
public final class ArtifactWorkspace implements AutoCloseable {
    private final Path root;
    private final Path tempRoot;

    ArtifactWorkspace(Path root, Path tempRoot) {
        this.root = root.toAbsolutePath().normalize();
        this.tempRoot = tempRoot.toAbsolutePath().normalize();
    }

    public Path root() { return root; }

    /** 解析并校验工作区内的相对路径。 */
    public Path resolve(String relativePath) {
        Path resolvedPath = root.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(root)) {
            throw new ServiceException("非法工作区路径，relativePath=" + relativePath);
        }
        return resolvedPath;
    }

    @Override
    public void close() {
        if (!root.startsWith(tempRoot)) {
            throw new ServiceException("工作区不位于受管临时根目录，root=" + root);
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException exception) { throw new ServiceException("清理工作区失败，path=" + path,
                        exception, BaseErrorCode.SERVICE_ERROR); }
            });
        } catch (IOException exception) {
            throw new ServiceException("清理工作区失败，root=" + root, exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
