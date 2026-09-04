package com.nexarag.auth.service;

import com.nexarag.auth.runner.AuthIdentityBloomRebuildRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** BloomFilter 单次重建启动入口测试。 */
class AuthIdentityBloomRebuildRunnerTest {

    @Test
    void shouldRunRequestedVersion() throws Exception {
        AuthIdentityBloomRebuildService rebuildService = mock(AuthIdentityBloomRebuildService.class);
        AuthIdentityBloomRebuildRunner runner = new AuthIdentityBloomRebuildRunner(2, rebuildService);

        runner.run(new DefaultApplicationArguments());

        verify(rebuildService).rebuild(2);
    }
}
