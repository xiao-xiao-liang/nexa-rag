package com.nexarag.common.error;

/**
 * 错误码接口，用于统一各模块错误码定义。
 */
public interface IErrorCode {

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    String code();

    /**
     * 获取错误信息。
     *
     * @return 错误信息
     */
    String message();
}
