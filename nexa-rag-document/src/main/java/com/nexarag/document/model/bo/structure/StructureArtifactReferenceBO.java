package com.nexarag.document.model.bo.structure;

/** 已发布解析结构制品的受控定位信息。 */
public record StructureArtifactReferenceBO(String type, String objectKey, String contentType, long size) {
}
