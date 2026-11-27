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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LicenseInfo} 单元测试：序列化能力 + 字段映射 + 有效期判断。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class LicenseInfoTest {

    /**
     * \u9a8c\u8bc1<code>roundtrip.getSubject()</code> \u7b49\u4e8e <code>"ioserver"</code>\uff0c<code>roundtrip.getConsumerAmount()</code> \u7b49\u4e8e <code>5</code>\uff0c<code>roundtrip.getExtra().getIp()</code> \u7b49\u4e8e <code>"10.0.0.1"</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    @Test
    void shouldBeSerializableForRemoteCache() throws Exception {
        LicenseExtraModel extra = new LicenseExtraModel();
        extra.setIp("10.0.0.1");
        extra.setMac("AA:BB:CC:DD:EE:FF");

        LicenseInfo original = LicenseInfo.builder()
                .subject("ioserver")
                .issued(new Date())
                .notBefore(new Date())
                .notAfter(new Date(System.currentTimeMillis() + 86_400_000L))
                .consumerType("user")
                .consumerAmount(5)
                .extra(extra)
                .build();

        // 模拟进入远程缓存前的 Java 序列化
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(original);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            LicenseInfo roundtrip = (LicenseInfo) in.readObject();

            assertThat(roundtrip.getSubject()).isEqualTo("ioserver");
            assertThat(roundtrip.getConsumerAmount()).isEqualTo(5);
            assertThat(roundtrip.getExtra()).isNotNull();
            assertThat(roundtrip.getExtra().getIp()).isEqualTo("10.0.0.1");
        }
    }

    /** \u9a8c\u8bc1<code>future.isValidNow()</code>\u4e3a\u5047\uff0c<code>expired.isValidNow()</code>\u4e3a\u5047\uff0c<code>boundary.isValidAt(now)</code>\u4e3a\u5047\u3002 */
    @Test
    void shouldEvaluateValidity() {
        Date now = new Date();

        LicenseInfo future = LicenseInfo.builder()
                .notBefore(new Date(now.getTime() + 86_400_000L))
                .build();
        assertThat(future.isValidNow()).isFalse();

        LicenseInfo expired = LicenseInfo.builder()
                .notBefore(new Date(now.getTime() - 172_800_000L))
                .notAfter(new Date(now.getTime() - 86_400_000L))
                .build();
        assertThat(expired.isValidNow()).isFalse();

        LicenseInfo active = LicenseInfo.builder()
                .notBefore(new Date(now.getTime() - 86_400_000L))
                .notAfter(new Date(now.getTime() + 86_400_000L))
                .build();
        assertThat(active.isValidNow()).isTrue();

        LicenseInfo boundary = LicenseInfo.builder().notAfter(now).build();
        assertThat(boundary.isValidAt(now)).isFalse();
    }

    /** \u9a8c\u8bc1<code>empty.hasAnyConstraint()</code>\u4e3a\u5047\uff0c<code>withIp.hasAnyConstraint()</code>\u4e3a\u771f\u3002 */
    @Test
    void extraModelShouldDetectConstraints() {
        LicenseExtraModel empty = new LicenseExtraModel();
        assertThat(empty.hasAnyConstraint()).isFalse();

        LicenseExtraModel withIp = new LicenseExtraModel();
        withIp.setIp("1.2.3.4");
        assertThat(withIp.hasAnyConstraint()).isTrue();
    }
}
