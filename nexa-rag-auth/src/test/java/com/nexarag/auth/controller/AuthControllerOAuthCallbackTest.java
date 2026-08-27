package com.nexarag.auth.controller;

import com.nexarag.auth.model.vo.OAuthCallbackVO;
import com.nexarag.auth.service.AuthenticationService;
import com.nexarag.auth.service.CurrentUserProfileService;
import com.nexarag.auth.service.DeviceSessionService;
import com.nexarag.auth.service.OAuthAuthenticationService;
import com.nexarag.auth.service.PasswordResetService;
import com.nexarag.auth.service.RegistrationService;
import com.nexarag.auth.web.CsrfTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuth 浏览器回调重定向测试。
 */
class AuthControllerOAuthCallbackTest {

    /**
     * 验证成功回调建立登录态后跳转到同源前端首页，而非直接返回 JSON 页面。
     */
    @Test
    void shouldRedirectSuccessfulOAuthCallbackToHome() {
        OAuthAuthenticationService oauthAuthenticationService = mock(OAuthAuthenticationService.class);
        when(oauthAuthenticationService.completeCallback("github", "code", "state", null))
                .thenReturn(new OAuthCallbackVO("LOGIN", null));
        AuthController controller = new AuthController(mock(AuthenticationService.class), mock(RegistrationService.class),
                mock(PasswordResetService.class), oauthAuthenticationService, mock(CsrfTokenService.class),
                mock(CurrentUserProfileService.class), mock(DeviceSessionService.class));

        ResponseEntity<Void> response = controller.completeOAuthCallback("github", "code", "state", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString("/home");
        verify(oauthAuthenticationService).completeCallback("github", "code", "state", null);
    }
}
