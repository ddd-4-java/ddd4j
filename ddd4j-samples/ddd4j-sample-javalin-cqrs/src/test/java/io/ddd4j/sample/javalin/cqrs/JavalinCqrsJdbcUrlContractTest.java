package io.ddd4j.sample.javalin.cqrs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavalinCqrsJdbcUrlContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void refusesNonBreakingSpaceJdbcUrlWithoutFallingBackToMemory() throws IOException, InterruptedException {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path log = temporaryDirectory.resolve("javalin-main.log");
        Process process = new ProcessBuilder(java, "-Dfile.encoding=UTF-8", "-Dsample.jdbc.url=\u00a0",
                "-cp", classpath, JavalinCqrsApplication.class.getName(), "0")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            boolean exited = process.waitFor(10, TimeUnit.SECONDS);
            String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
            assertTrue(exited, "Javalin CQRS main did not terminate for an invalid JDBC URL\n" + output);
            assertEquals(1, process.exitValue(), output);
            assertTrue(output.contains("IllegalArgumentException: URL invalid")
                    && output.contains("org.postgresql.ds.common.BaseDataSource.setUrl"), output);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Javalin CQRS main did not stop after forced termination");
            }
        }
    }
}
