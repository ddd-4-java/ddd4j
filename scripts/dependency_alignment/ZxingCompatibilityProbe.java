import com.google.zxing.QrCodes;
import com.google.zxing.model.QrCodeOutput;
/** 验证两个发布坐标的二维码编码解码行为。 */
public class ZxingCompatibilityProbe {
    /** 使用固定中英文输入验证编码解码及尺寸、格式输出；仅在 JDK 17/21 运行。 */
    public static void main(String[] args) {
        for (String content : new String[] {"ddd4j-jdk-parity", "中文二维码-2026", "https://example.invalid/path?a=1"}) {
            QrCodeOutput output = QrCodes.encode(content);
            String actual = QrCodes.decode(output.getBytes()).getText();
            if (!content.equals(actual)) {
                throw new AssertionError(actual);
            }
            System.out.println(actual + "|" + output.getWidth() + "|" + output.getHeight() + "|" + output.getMimeType());
        }
    }
}
