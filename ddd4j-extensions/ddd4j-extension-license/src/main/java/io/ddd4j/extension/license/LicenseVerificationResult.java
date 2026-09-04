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

    private final boolean valid;
    private final Status status;
    private final String message;
    private final LicenseInfo licenseInfo;

    public static LicenseVerificationResult success(LicenseInfo licenseInfo) {
        return LicenseVerificationResult.builder()
                .valid(true)
                .status(Status.VALID)
                .message("License 校验通过")
                .licenseInfo(licenseInfo)
                .build();
    }

    public static LicenseVerificationResult disabled() {
        return LicenseVerificationResult.builder()
                .valid(true)
                .status(Status.DISABLED)
                .message("License 校验已关闭")
                .build();
    }

    public static LicenseVerificationResult failure(Status status, String message) {
        Objects.requireNonNull(status, "status 不能为空");
        return LicenseVerificationResult.builder()
                .valid(false)
                .status(status)
                .message(message)
                .build();
    }

    public enum Status {
        VALID,
        DISABLED,
        NOT_INSTALLED,
        ENVIRONMENT_MISMATCH,
        INVALID
    }
}
