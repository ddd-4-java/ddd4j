package io.ddd4j.spring.cqrs;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基础模式在真正缺失 ORM、持久化 API 和事件源的独立进程中启动。
 */
class SpringOptionalClasspathTest {
    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>BasicSpringProcess.class.getName()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void startsWithoutJpaAndEventStoreClasses() throws Exception {
        assertStarts(BasicSpringProcess.class.getName(), "basic-spring");
    }

    /**
     * \u9a8c\u8bc1\u7f3a\u5c11 JPA \u548c\u4e8b\u4ef6\u5b58\u50a8\u4f9d\u8d56\u65f6\uff0c\u65e0\u5305\u540d Spring \u63a2\u9488\u4ecd\u53ef\u542f\u52a8\u3002
     * @throws Exception \u51c6\u5907\u6d4b\u8bd5\u6570\u636e\u5e93\u3001\u542f\u52a8\u6846\u67b6\u6216\u6267\u884c\u4e1a\u52a1\u5408\u540c\u5931\u8d25
     */
    @Test
    void startsFromUnnamedPackageWithoutJpaAndEventStoreClasses() throws Exception {
        assertStarts("UnnamedBasicSpringProcess", "unnamed-basic-spring");
    }

    /**
     * \u6838\u5bf9<code>process.waitFor(30, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>process.exitValue()</code> \u7b49\u4e8e <code>0</code>\u3002
     * @param mainClass \u4f20\u7ed9 <code>List.of()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param logPrefix \u4f20\u7ed9 <code>Files.writeString()</code> \u7684 <code>String</code> \u8f93\u5165
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u3001\u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
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
