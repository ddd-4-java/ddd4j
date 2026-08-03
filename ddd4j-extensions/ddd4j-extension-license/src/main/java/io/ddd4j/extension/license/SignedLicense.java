package io.ddd4j.extension.license;

import io.ddd4j.kit.lang.StrKit;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * ddd4j 签名许可证的文本封装格式。
 *
 * <p>文件内容只包含格式版本、签名算法、Base64 编码的 JSON 负载和签名，
 * 不使用 Java 对象反序列化或 XMLDecoder。</p>
 */
public final class SignedLicense {

    private static final String FORMAT = "DDD4J-LICENSE-1";
    private static final int FIELD_COUNT = 4;

    private final String algorithm;
    private final byte[] payload;
    private final byte[] signature;

    public SignedLicense(String algorithm, byte[] payload, byte[] signature) {
        if (StrKit.isBlank(algorithm)) {
            throw new IllegalArgumentException("license signature algorithm must not be blank");
        }
        this.algorithm = algorithm;
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.signature = Objects.requireNonNull(signature, "signature must not be null");
    }

    public String algorithm() {
        return algorithm;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public byte[] signature() {
        return signature.clone();
    }

    public String serialize() {
        return String.join("\n", FORMAT, algorithm,
                Base64.getEncoder().encodeToString(payload),
                Base64.getEncoder().encodeToString(signature)) + "\n";
    }

    public static SignedLicense parse(byte[] bytes) {
        String[] fields = new String(bytes, StandardCharsets.UTF_8).split("\\R", -1);
        if (fields.length != FIELD_COUNT + 1 || !Objects.equals(FORMAT, fields[0])) {
            throw new IllegalArgumentException("Unsupported ddd4j license format");
        }
        try {
            return new SignedLicense(fields[1], Base64.getDecoder().decode(fields[2]), Base64.getDecoder().decode(fields[3]));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid ddd4j license encoding", exception);
        }
    }

}
