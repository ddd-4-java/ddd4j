package io.ddd4j.kit.text;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证公共字节常量不依赖进程默认字符集。
 */
class StrPoolCharsetTest {

    /**
     * \u9a8c\u8bc1<code>process.waitFor()</code> \u7b49\u4e8e <code>0</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
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

    /** \u5728\u72ec\u7acb JVM \u4e2d\u9a8c\u8bc1\u9ed8\u8ba4\u7f16\u7801\u53d8\u5316\u4e0d\u4f1a\u5f71\u54cd\u6362\u884c\u5e38\u91cf\u7684 UTF-8 \u5b57\u8282\u3002 */
    public static final class EncodingProbe {

        /**
         * \u6bd4\u8f83\u9ed8\u8ba4\u7f16\u7801\u4e3a UTF-16LE \u65f6\u7684\u6362\u884c\u5e38\u91cf\u5b57\u8282\u4e0e\u663e\u5f0f UTF-8 \u7f16\u7801\uff0c\u5e76\u901a\u8fc7\u9000\u51fa\u7801\u6807\u8bb0\u5931\u8d25\u3002
         * @param args \u547d\u4ee4\u884c\u53c2\u6570\uff0c\u7f16\u7801\u7531 JVM \u542f\u52a8\u914d\u7f6e\u63d0\u4f9b
         */
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
