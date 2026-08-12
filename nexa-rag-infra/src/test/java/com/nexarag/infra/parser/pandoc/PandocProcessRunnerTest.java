package com.nexarag.infra.parser.pandoc;

import com.nexarag.infra.config.PandocProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pandoc 进程执行器测试。
 */
class PandocProcessRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void runShouldDrainLargeStderrWhileProcessIsRunning() {
        PandocProperties properties = new PandocProperties();
        properties.setTimeout(Duration.ofSeconds(3));
        properties.setMaxStderrBytes(64);
        PandocProcessRunner runner = new PandocProcessRunner(properties);

        assertThatCode(() -> runner.run(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                        "[Console]::Error.Write(('x' * 131072))"), tempDir))
                .doesNotThrowAnyException();
    }
}
