package com.nexarag.retrieval.chat.model;

import java.util.List;

/**
 * 问题意图识别结果。
 *
 * @param intentIds 命中的意图标识
 * @param confidence 最高意图置信度
 */
public record IntentRecognitionResult(List<String> intentIds, double confidence) {
}
