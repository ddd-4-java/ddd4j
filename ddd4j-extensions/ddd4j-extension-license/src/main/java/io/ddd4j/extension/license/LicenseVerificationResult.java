package io.ddd4j.extension.license;

import lombok.Builder;
import lombok.Getter;

import java.util.Objects;

/**
 * License 结构化验证结果。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
@Builder
public class LicenseVerificationResult {

    /** \u672c\u6b21\u8bb8\u53ef\u8bc1\u9a8c\u8bc1\u662f\u5426\u6210\u529f\u3002 */
    private final boolean valid;
    /** \u5bf9\u5916\u53ef\u533a\u5206\u7684\u8bb8\u53ef\u8bc1\u9a8c\u8bc1\u7ed3\u679c\u7c7b\u578b\u3002 */
    private final Status status;
    /** \u8bb8\u53ef\u8bc1\u9a8c\u8bc1\u7ed3\u679c\u7684\u8bca\u65ad\u8bf4\u660e\u3002 */
    private final String message;
    /** \u9a8c\u8bc1\u6210\u529f\u540e\u4ea4\u7ed9\u8c03\u7528\u65b9\u7684\u8bb8\u53ef\u8bc1\u4fe1\u606f\u3002 */
    private final LicenseInfo licenseInfo;

    /**
     * \u6784\u9020\u6821\u9a8c\u901a\u8fc7\u7684\u7ed3\u679c\u3002
     * @param licenseInfo \u89e3\u6790\u51fa\u7684\u8bb8\u53ef\u8bc1\u4fe1\u606f
     * @return \u6821\u9a8c\u901a\u8fc7\u7684\u7ed3\u679c\uff08valid=true\uff09
     */
    public static LicenseVerificationResult success(LicenseInfo licenseInfo) {
        return LicenseVerificationResult.builder()
                .valid(true)
                .status(Status.VALID)
                .message("License 校验通过")
                .licenseInfo(licenseInfo)
                .build();
    }

    /**
     * \u6784\u9020\u8bb8\u53ef\u8bc1\u6821\u9a8c\u5173\u95ed\u65f6\u7684\u7ed3\u679c\u3002
     * @return \u6821\u9a8c\u5df2\u5173\u95ed\u7684\u7ed3\u679c\uff08valid=true\uff0c\u72b6\u6001 DISABLED\uff09
     */
    public static LicenseVerificationResult disabled() {
        return LicenseVerificationResult.builder()
                .valid(true)
                .status(Status.DISABLED)
                .message("License 校验已关闭")
                .build();
    }

    /**
     * \u6784\u9020\u6821\u9a8c\u5931\u8d25\u7684\u7ed3\u679c\u3002
     * @param status \u5931\u8d25\u72b6\u6001
     * @param message \u5931\u8d25\u63cf\u8ff0\u4fe1\u606f
     * @return \u6821\u9a8c\u5931\u8d25\u7684\u7ed3\u679c\uff08valid=false\uff09
     */
    public static LicenseVerificationResult failure(Status status, String message) {
        Objects.requireNonNull(status, "status 不能为空");
        return LicenseVerificationResult.builder()
                .valid(false)
                .status(status)
                .message(message)
                .build();
    }

    /** \u8bb8\u53ef\u8bc1\u6821\u9a8c\u72b6\u6001\u3002 */
    public enum Status {
        /** \u6821\u9a8c\u901a\u8fc7\u3002 */
        VALID,
        /** \u8bb8\u53ef\u8bc1\u6821\u9a8c\u5df2\u5173\u95ed\u3002 */
        DISABLED,
        /** \u5c1a\u672a\u5b89\u88c5\u8bb8\u53ef\u8bc1\u3002 */
        NOT_INSTALLED,
        /** \u8fd0\u884c\u73af\u5883\u4e0e\u8bb8\u53ef\u8bc1\u4e0d\u5339\u914d\u3002 */
        ENVIRONMENT_MISMATCH,
        /** \u8bb8\u53ef\u8bc1\u65e0\u6548\u3002 */
        INVALID
    }
}
