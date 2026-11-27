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
     * 验证<code>process.waitFor()</code> 等于 <code>0</code>。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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

    /**
     * 在独立 JVM 中验证默认编码变化不会影响换行常量的 UTF-8 字节。
     */
    public static final class EncodingProbe {

        /**
         * 比较默认编码为 UTF-16LE 时的换行常量字节与显式 UTF-8 编码，并通过退出码标记失败。
         *
         * @param args 命令行参数，编码由 JVM 启动配置提供
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
