package com.nexarag.model.constants;

/**
 * vLLM Tokenizer 调用使用的稳定接口和算法标识。
 */
public final class VllmApiConstant {

    public static final String OPENAI_VERSION_PATH = "/v1";
    public static final String TOKENIZE_PATH = "/tokenize";
    public static final String SHA_256_ALGORITHM = "SHA-256";
    public static final String CONTENT_SEPARATOR = "\n";

    private VllmApiConstant() {
    }
}
