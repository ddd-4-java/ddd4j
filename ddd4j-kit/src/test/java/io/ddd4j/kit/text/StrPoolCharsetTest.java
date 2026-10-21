package io.ddd4j.kit.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** 验证公共字节常量不依赖进程默认字符集。 */
class StrPoolCharsetTest {

    @Test
    void newlineBytesUseUtf8InNonUtf8Process() throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String classes = new File(StrPool.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getAbsolutePath();
        String tests = new File(EncodingProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getAbsolutePath();
        Process process = new ProcessBuilder(java, "-Dfile.encoding=UTF-16LE", "-cp",
                classes + File.pathSeparator + tests, EncodingProbe.class.getName()).start();

        assertEquals(0, process.waitFor(), "StrPool bytes must match UTF-8 in a UTF-16LE JVM");
    }

    public static final class EncodingProbe {

        public static void main(String[] args) {
            if (!"UTF-16LE".equalsIgnoreCase(Charset.defaultCharset().name())) {
                System.exit(2);
            }
            if (!Arrays.equals(StrPool.NEWLINE.getBytes(StandardCharsets.UTF_8), StrPool.BYTES_NEW_LINE)) {
                System.exit(1);
            }
        }
    }
}
