/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.javalin.cqrs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 启动独立 JVM 验证真实商品交易入口校验数据库配置并释放资源。 */
class JavalinCqrsJdbcUrlContractTest {

    /** 保存子进程诊断输出。 */
    @TempDir
    Path temporaryDirectory;

    /** 未提供数据库 URL 时在提供 HTTP 服务前退出。 */
    @Test
    void refusesMissingJdbcUrl() throws Exception {
        assertStartupFailure(null, "缺少环境变量 CATALOG_JDBC_URL");
    }

    /** 仅包含空白的 URL 与缺失配置同样失败。 */
    @Test
    void refusesBlankJdbcUrl() throws Exception {
        assertStartupFailure(" ", "缺少环境变量 CATALOG_JDBC_URL");
    }

    /** 非空的其他数据库 URL 必须在访问数据库前被拒绝。 */
    @Test
    void rejectsNonMysqlUrl() throws Exception {
        assertStartupFailure("jdbc:invalid:catalog", "CATALOG_JDBC_URL 必须指定专用 MySQL 数据库");
    }

    /** 保留无需处理受检异常的公开 main 签名。 */
    @Test
    void keepsMainCallableWithoutHandlingCheckedExceptions() throws NoSuchMethodException {
        assertEquals(0, JavalinCqrsApplication.class.getMethod("main", String[].class).getExceptionTypes().length);
    }

    /**
     * 使用独立进程环境启动示例并验证退出原因。
     * @param jdbcUrl 传给入口的 URL，空引用表示删除环境变量
     * @param expected 业务配置校验应报告的消息
     * @throws IOException 无法启动进程或读取输出
     * @throws InterruptedException 等待子进程时被中断
     */
    private void assertStartupFailure(String jdbcUrl, String expected)
            throws IOException, InterruptedException {
        String testClasspath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        String classpath = Arrays.stream(testClasspath.split(Pattern.quote(File.pathSeparator)))
                .filter(entry -> !Paths.get(entry).endsWith("test-classes"))
                .collect(Collectors.joining(File.pathSeparator));
        List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Dsample.port=0");
        command.add("-cp");
        command.add(classpath);
        command.add(JavalinCqrsApplication.class.getName());

        Path log = Files.createTempFile(temporaryDirectory, "javalin-cqrs-", ".log");
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        Map<String, String> environment = builder.environment();
        environment.put("CATALOG_DB_USER", "sample");
        environment.put("CATALOG_DB_PASSWORD", "sample");
        if (Objects.isNull(jdbcUrl)) {
            environment.remove("CATALOG_JDBC_URL");
        } else {
            environment.put("CATALOG_JDBC_URL", jdbcUrl);
        }
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "CQRS main did not exit after invalid JDBC configuration");
            String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
            assertNotEquals(0, process.exitValue(), output);
            assertTrue(output.contains(expected), output);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "CQRS main did not stop after forced termination");
            }
        }
    }
}
