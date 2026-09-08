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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.springframework.util.StringUtils;

/**
 * 自定义KeyStoreParam，用于将公私钥存储文件存放到其他磁盘位置而不是项目中。现场使用的时候公钥大部分都不会放在项目中的
 */
public class CustomKeyStoreParam {

    public static final String LEGACY_SIGNATURE_ALGORITHM = "SHA1withDSA";

    /**
     * 公钥/私钥在磁盘上的存储路径
     */
    private final String storePath;
    private final String alias;
    private final String storePwd;
    private final String keyPwd;
    private final String signatureAlgorithm;

    public CustomKeyStoreParam(Class<?> clazz, String resource, String alias, String storePwd, String keyPwd) {
        this(clazz, resource, alias, storePwd, keyPwd, LEGACY_SIGNATURE_ALGORITHM);
    }

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


    public String getAlias() {
        return alias;
    }

    public String getStorePwd() {
        return storePwd;
    }

    public String getKeyPwd() {
        return keyPwd;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    /**
     * AbstractKeyStoreParam里面的getStream()方法默认文件是存储的项目中。
     * 用于将公私钥存储文件存放到其他磁盘位置而不是项目中
     */
    public InputStream getStream() throws IOException {
        return new FileInputStream(new File(storePath));
    }

}
