package com.nexarag.infra.parser.model;

import com.nexarag.infra.parser.workspace.ArtifactWorkspace;

import java.nio.file.Path;

/**
 * 已写入受管工作区的原始文档，供格式处理器复用而无需再次从对象存储下载。
 *
 * @param sourcePath 原始文档本地路径
 * @param workspace  当前任务工作区
 */
public record StagedDocumentBO(Path sourcePath, ArtifactWorkspace workspace) {
}
