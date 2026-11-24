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
package io.ddd4j.sample.vertx.cqrs;

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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertxCqrsApplicationMainTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void refusesMissingJdbcUrl() throws Exception {
        assertStartupFailure(Collections.emptyList(), "NullPointerException: sample.jdbc.url required");
    }

    @Test
    void refusesBlankJdbcUrl() throws Exception {
        assertStartupFailure(Collections.singletonList("-Dsample.jdbc.url= "),
                "IllegalArgumentException: sample.jdbc.url required");
    }

    @Test
    void keepsMainCallableWithoutHandlingCheckedExceptions() throws NoSuchMethodException {
        assertEquals(0, VertxCqrsApplication.class.getMethod("main", String[].class).getExceptionTypes().length);
    }

    private void assertStartupFailure(List<String> properties, String expected)
            throws IOException, InterruptedException {
        String testClasspath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        String classpath = Arrays.stream(testClasspath.split(Pattern.quote(File.pathSeparator)))
                .filter(entry -> !Paths.get(entry).endsWith("test-classes"))
                .collect(Collectors.joining(File.pathSeparator));
        List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Dsample.port=0");
        command.addAll(properties);
        command.add("-cp");
        command.add(classpath);
        command.add(VertxCqrsApplication.class.getName());

        Path log = Files.createTempFile(temporaryDirectory, "vertx-cqrs-", ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(8, TimeUnit.SECONDS), "CQRS main did not exit after invalid JDBC configuration");
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
