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

import java.util.Collections;
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

    /** \u8bfb\u53d6\u672c\u673a\u7f51\u7edc\u5730\u5740\u53ca\u786c\u4ef6\u6807\u8bc6\u7684\u63d0\u4f9b\u5668\u3002 */
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
                && !matchesAny(expected.getSn(), Collections.singleton(machineInfoProvider.serialNumber()), this::normalizeText)) {
            throw new IllegalStateException("SN 校验失败: 当前机器序列号不在授权范围内");
        }
    }

    /**
     * \u6309\u914d\u7f6e\u7684\u5206\u9694\u7b26\u63d0\u53d6\u671f\u671b\u503c\uff0c\u89c4\u8303\u5316\u540e\u68c0\u67e5\u662f\u5426\u547d\u4e2d\u5b9e\u9645\u4fe1\u606f\u3002
     * @param expectedValues \u8bb8\u53ef\u8bc1\u4e2d\u5141\u8bb8\u7684\u914d\u7f6e\u503c
     * @param actualValues \u4ece\u5f53\u524d\u673a\u5668\u53d6\u5f97\u7684\u5b9e\u9645\u503c\u96c6\u5408
     * @param normalizer \u5f52\u4e00\u5316\u6807\u8bc6\u7684\u51fd\u6570
     * @return \u672a\u914d\u7f6e\u9650\u5236\u6216\u81f3\u5c11\u6709\u4e00\u9879\u5339\u914d\u65f6\u8fd4\u56de true
     */
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

    /**
     * \u5c06\u975e\u7a7a\u6807\u8bc6\u53bb\u6389\u9996\u5c3e\u7a7a\u683c\u5e76\u6309\u56fa\u5b9a\u533a\u57df\u8f6c\u4e3a\u5c0f\u5199\u3002
     * @param value \u5f85\u5f52\u4e00\u5316\u7684\u73af\u5883\u6807\u8bc6
     * @return \u5f52\u4e00\u5316\u7ed3\u679c\uff0c\u7a7a\u5f15\u7528\u539f\u6837\u8fd4\u56de
     */
    private String normalizeText(String value) {
        if (Objects.isNull(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * \u89c4\u8303\u5316 MAC \u5730\u5740\u5927\u5c0f\u5199\u5e76\u53bb\u9664\u5192\u53f7\u548c\u8fde\u5b57\u7b26\u3002
     * @param value \u5f85\u6bd4\u8f83\u7684 MAC \u5730\u5740
     * @return \u65e0\u5206\u9694\u7b26\u7684\u5c0f\u5199\u5730\u5740\uff0c\u7a7a\u5f15\u7528\u539f\u6837\u8fd4\u56de
     */
    private String normalizeMac(String value) {
        String normalized = normalizeText(value);
        if (Objects.isNull(normalized)) {
            return null;
        }
        return normalized.replace(":", "").replace("-", "");
    }

}
