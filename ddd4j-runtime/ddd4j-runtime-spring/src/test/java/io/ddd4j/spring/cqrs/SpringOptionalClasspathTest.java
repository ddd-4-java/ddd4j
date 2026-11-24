package io.ddd4j.spring.cqrs;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** 基础模式在真正缺失 ORM、持久化 API 和事件源的独立进程中启动。 */
class SpringOptionalClasspathTest {
    @Test
    void startsWithoutJpaAndEventStoreClasses() throws Exception {
        assertStarts(BasicSpringProcess.class.getName(), "basic-spring");
    }

    @Test
    void startsFromUnnamedPackageWithoutJpaAndEventStoreClasses() throws Exception {
        assertStarts("UnnamedBasicSpringProcess", "unnamed-basic-spring");
    }

    private void assertStarts(String mainClass, String logPrefix) throws Exception {
        String classpath = Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                .filter(path -> !path.contains("jakarta.persistence") && !path.contains("spring-data-")
                        && !path.contains("hibernate") && !path.contains("ddd4j-data-event-store"))
                .collect(Collectors.joining(java.io.File.pathSeparator));
        List<String> command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, mainClass);
        Files.writeString(Path.of("target", logPrefix + "-command.txt"), String.join("\n", command));
        Path log = Path.of("target", logPrefix + "-process.log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Isolated context timed out");
        String output = Files.readString(log);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("BASIC_SPRING_READY_WITHOUT_JPA_OR_EVENTSTORE"), output);
    }
}
