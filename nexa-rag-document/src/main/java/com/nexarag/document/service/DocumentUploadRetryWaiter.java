package com.nexarag.document.service;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import org.springframework.stereotype.Component;

/**
 * 文档上传重试等待器，负责在对象存储重试前执行短退避。
 */
@Component
public class DocumentUploadRetryWaiter {

    /**
     * 等待指定时间。
     *
     * @param millis 等待毫秒数
     */
    public void await(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException("文档上传重试等待被中断", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
