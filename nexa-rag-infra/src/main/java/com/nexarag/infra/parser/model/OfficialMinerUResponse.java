package com.nexarag.infra.parser.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * MinerU 官方接口响应，承载文件上传申请和批次解析结果。
 *
 * @param code 接口业务状态码
 * @param msg 接口处理信息
 * @param data 接口响应数据
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OfficialMinerUResponse(int code, String msg, Data data) {

    /**
     * MinerU 官方接口响应数据。
     *
     * @param batchId 批次ID
     * @param fileUrls 签名上传地址列表
     * @param extractResult 批次解析结果列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@JsonProperty("batch_id") String batchId,
                @JsonProperty("file_urls") List<String> fileUrls,
                @JsonProperty("extract_result") List<ExtractResult> extractResult) {
    }

    /**
     * MinerU 单文件解析结果。
     *
     * @param fileName 文件名
     * @param state 解析状态
     * @param fullZipUrl 完整ZIP产物地址
     * @param errMsg 失败原因
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractResult(@JsonProperty("file_name") String fileName,
                         String state,
                         @JsonProperty("full_zip_url") String fullZipUrl,
                         @JsonProperty("err_msg") String errMsg) {
    }
}
