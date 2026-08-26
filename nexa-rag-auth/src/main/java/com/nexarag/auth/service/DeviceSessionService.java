package com.nexarag.auth.service;

import com.nexarag.auth.model.vo.DeviceSessionVO;

import java.util.List;

/**
 * 管理当前账号的设备会话摘要和指定会话撤销。
 */
public interface DeviceSessionService {

    void recordCurrentLogin(Long userId);

    void touchCurrentSession();

    List<DeviceSessionVO> listCurrentUserSessions();

    void kickoutCurrentUserSession(Long deviceSessionId);

    void logoutAllCurrentUserSessions();

    void logoutCurrentSession();
}
