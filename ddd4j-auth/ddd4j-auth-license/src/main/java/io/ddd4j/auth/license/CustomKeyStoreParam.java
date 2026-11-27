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
package io.ddd4j.auth.license;

import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * 自定义KeyStoreParam，用于将公私钥存储文件存放到其他磁盘位置而不是项目中。现场使用的时候公钥大部分都不会放在项目中的
 */
public class CustomKeyStoreParam {

    /**
     * 兼容旧密钥库使用的默认签名算法标识。
     */
    public static final String LEGACY_SIGNATURE_ALGORITHM = "SHA1withDSA";

    /**
     * 公钥/私钥在磁盘上的存储路径
     */
    private final String storePath;
    /** \u4ece\u5bc6\u94a5\u5e93\u4e2d\u9009\u62e9\u8bc1\u4e66\u6216\u5bc6\u94a5\u6761\u76ee\u7684\u522b\u540d\u3002 */
    private final String alias;
    /** \u6253\u5f00\u5bc6\u94a5\u5e93\u6240\u9700\u7684\u5b58\u50a8\u53e3\u4ee4\u3002 */
    private final String storePwd;
    /** \u89e3\u9501\u5bc6\u94a5\u5e93\u4e2d\u7b7e\u540d\u79c1\u94a5\u6761\u76ee\u7684\u53e3\u4ee4\u3002 */
    private final String keyPwd;
    /** \u7528\u4e8e\u7b7e\u53d1\u6216\u9a8c\u8bc1\u8bb8\u53ef\u8bc1\u7684\u7b7e\u540d\u7b97\u6cd5\u3002 */
    private final String signatureAlgorithm;

    /**
     * \u4f7f\u7528\u517c\u5bb9\u7b7e\u540d\u7b97\u6cd5\u521b\u5efa\u5bc6\u94a5\u5e93\u53c2\u6570\u3002
     * @param clazz \u8c03\u7528\u65b9\u7c7b\u578b
     * @param resource \u5bc6\u94a5\u5e93\u6587\u4ef6\u8def\u5f84
     * @param alias \u5bc6\u94a5\u522b\u540d
     * @param storePwd \u5bc6\u94a5\u5e93\u5bc6\u7801
     * @param keyPwd \u79c1\u94a5\u5bc6\u7801\uff0c\u53ef\u4e3a\u7a7a
     */
    public CustomKeyStoreParam(Class<?> clazz, String resource, String alias, String storePwd, String keyPwd) {
        this(clazz, resource, alias, storePwd, keyPwd, LEGACY_SIGNATURE_ALGORITHM);
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u7b7e\u540d\u7b97\u6cd5\u521b\u5efa\u5bc6\u94a5\u5e93\u53c2\u6570\u3002
     * @param clazz \u8c03\u7528\u65b9\u7c7b\u578b
     * @param resource \u5bc6\u94a5\u5e93\u6587\u4ef6\u8def\u5f84
     * @param alias \u5bc6\u94a5\u522b\u540d
     * @param storePwd \u5bc6\u94a5\u5e93\u5bc6\u7801
     * @param keyPwd \u79c1\u94a5\u5bc6\u7801\uff0c\u53ef\u4e3a\u7a7a
     * @param signatureAlgorithm \u7b7e\u540d\u7b97\u6cd5
     */
    public CustomKeyStoreParam(Class<?> clazz, String resource, String alias, String storePwd, String keyPwd,
                               String signatureAlgorithm) {
        Objects.requireNonNull(clazz, "clazz");
        this.storePath = Objects.requireNonNull(resource, "resource");
        this.alias = Objects.requireNonNull(alias, "alias");
        this.storePwd = Objects.requireNonNull(storePwd, "storePwd");
        this.keyPwd = keyPwd;
        if (!StringUtils.hasText(signatureAlgorithm)) {
            throw new IllegalArgumentException("signatureAlgorithm must not be blank");
        }
        this.signatureAlgorithm = signatureAlgorithm;
    }


    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getAlias() {
        return alias;
    }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getStorePwd() {
        return storePwd;
    }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getKeyPwd() {
        return keyPwd;
    }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    /**
     * AbstractKeyStoreParam\u91cc\u9762\u7684getStream()\u65b9\u6cd5\u9ed8\u8ba4\u6587\u4ef6\u662f\u5b58\u50a8\u7684\u9879\u76ee\u4e2d\u3002
     *  \u7528\u4e8e\u5c06\u516c\u79c1\u94a5\u5b58\u50a8\u6587\u4ef6\u5b58\u653e\u5230\u5176\u4ed6\u78c1\u76d8\u4f4d\u7f6e\u800c\u4e0d\u662f\u9879\u76ee\u4e2d
     *
     * @return \u5bc6\u94a5\u5e93\u8f93\u5165\u6d41
     * @throws IOException \u5bc6\u94a5\u5e93\u6587\u4ef6\u65e0\u6cd5\u8bfb\u53d6\u65f6
     */
    public InputStream getStream() throws IOException {
        return new FileInputStream(new File(storePath));
    }

}
