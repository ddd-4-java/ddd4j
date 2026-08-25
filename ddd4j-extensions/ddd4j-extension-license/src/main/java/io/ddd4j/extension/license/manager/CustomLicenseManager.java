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
package io.ddd4j.extension.license.manager;

import io.ddd4j.extension.license.LicenseExtraModel;
import io.ddd4j.extension.license.LicenseInfo;
import io.ddd4j.extension.license.machine.DefaultLicenseMachineInfoProvider;
import io.ddd4j.extension.license.machine.LicenseMachineInfoProvider;
import io.ddd4j.kit.lang.StrKit;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * License 的运行环境约束校验器。
 *
 * <p>该实现只校验已完成签名验证的许可证内容，不处理密钥存储或反序列化，
 * 从而让密码学边界保持在签发和安装流程中。</p>
 */
public class CustomLicenseManager {

    private final LicenseMachineInfoProvider machineInfoProvider;

    /**
     * 使用默认机器信息提供器创建校验器。
     */
    public CustomLicenseManager() {
        this(DefaultLicenseMachineInfoProvider.INSTANCE);
    }

    /**
     * 使用指定机器信息提供器创建校验器。
     *
     * @param machineInfoProvider 机器信息提供器
     */
    public CustomLicenseManager(LicenseMachineInfoProvider machineInfoProvider) {
        this.machineInfoProvider = Objects.requireNonNull(machineInfoProvider, "machineInfoProvider 不能为空");
    }

    /**
     * 校验许可证中的机器约束。
     *
     * @param licenseInfo 已完成签名验证的许可证内容
     */
    public void validate(LicenseInfo licenseInfo) {
        Objects.requireNonNull(licenseInfo, "licenseInfo 不能为空");
        validateExtra(licenseInfo.getExtra());
    }

    /**
     * 校验 IP、MAC 与机器序列号约束。
     *
     * @param expected 许可证声明的约束
     */
    public void validateExtra(LicenseExtraModel expected) {
        if (Objects.isNull(expected) || !expected.hasAnyConstraint()) {
            return;
        }
        if (!matchesAny(expected.getIp(), machineInfoProvider.ipAddresses(), this::normalizeText)) {
            throw new IllegalStateException("IP 校验失败: 当前机器地址不在授权范围内");
        }
        if (!matchesAny(expected.getMac(), machineInfoProvider.macAddresses(), this::normalizeMac)) {
            throw new IllegalStateException("MAC 校验失败: 当前机器地址不在授权范围内");
        }
        if (Objects.nonNull(expected.getSn())
                && !matchesAny(expected.getSn(), Set.of(machineInfoProvider.serialNumber()), this::normalizeText)) {
            throw new IllegalStateException("SN 校验失败: 当前机器序列号不在授权范围内");
        }
    }

    private boolean matchesAny(String expectedValues, Set<String> actualValues, Function<String, String> normalizer) {
        if (Objects.isNull(expectedValues)) {
            return true;
        }
        if (Objects.isNull(actualValues) || actualValues.isEmpty()) {
            return false;
        }
        for (String expectedValue : expectedValues.split("[,;\\s]+")) {
            String normalizedExpected = normalizer.apply(expectedValue);
            if (StrKit.isEmpty(normalizedExpected)) {
                continue;
            }
            for (String actualValue : actualValues) {
                if (Objects.equals(normalizedExpected, normalizer.apply(actualValue))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizeText(String value) {
        if (Objects.isNull(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMac(String value) {
        String normalized = normalizeText(value);
        if (Objects.isNull(normalized)) {
            return null;
        }
        return normalized.replace(":", "").replace("-", "");
    }

}
