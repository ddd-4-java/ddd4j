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
     * 在测试框架中执行 <code>BasicSpringProcess.class.getName()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void startsWithoutJpaAndEventStoreClasses() throws Exception {
        assertStarts(BasicSpringProcess.class.getName(), "basic-spring");
    }

    /**
     * 验证缺少 JPA 和事件存储依赖时，无包名 Spring 探针仍可启动。
     *
     * @throws Exception 准备测试数据库、启动框架或执行业务合同失败
     */
    @Test
    void startsFromUnnamedPackageWithoutJpaAndEventStoreClasses() throws Exception {
        assertStarts("UnnamedBasicSpringProcess", "unnamed-basic-spring");
    }

    /**
     * 核对<code>process.waitFor(30, TimeUnit.SECONDS)</code>为真，<code>process.exitValue()</code> 等于 <code>0</code>。
     *
     * @param mainClass 传给 <code>List.of()</code> 的 <code>String</code> 输入
     * @param logPrefix 传给 <code>Files.writeString()</code> 的 <code>String</code> 输入
     * @throws Exception 准备或读取夹具文件、等待并发操作时失败
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
