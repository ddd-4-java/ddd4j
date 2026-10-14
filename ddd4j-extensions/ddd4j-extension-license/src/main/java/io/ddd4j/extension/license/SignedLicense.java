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

    /**
     * 创建签名许可证。
     *
     * @param algorithm 签名算法
     * @param payload 许可证负载
     * @param signature 签名字节
     */
    public SignedLicense(String algorithm, byte[] payload, byte[] signature) {
        if (StrKit.isBlank(algorithm)) {
            throw new IllegalArgumentException("license signature algorithm must not be blank");
        }
        this.algorithm = algorithm;
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.signature = Objects.requireNonNull(signature, "signature must not be null");
    }

    /**
     * 从文本格式解析签名许可证。
     *
     * @param bytes 文本格式字节
     * @return 签名许可证
     */
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

    /**
     * 获取签名算法。
     *
     * @return 签名算法
     */
    public String algorithm() {
        return algorithm;
    }

    /**
     * 获取许可证负载副本。
     *
     * @return 许可证负载副本
     */
    public byte[] payload() {
        return payload.clone();
    }

    /**
     * 获取签名字节副本。
     *
     * @return 签名字节副本
     */
    public byte[] signature() {
        return signature.clone();
    }

    /**
     * 序列化签名许可证为文本格式。
     *
     * @return 文本格式许可证
     */
    public String serialize() {
        return String.join("\n", FORMAT, algorithm,
                Base64.getEncoder().encodeToString(payload),
                Base64.getEncoder().encodeToString(signature)) + "\n";
    }

}
