package io.ddd4j.extension.license;

import de.schlichtherle.license.LicenseContent;
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

    @Test
    void shouldMapFromLicenseContent() {
        LicenseContent content = new LicenseContent();
        content.setSubject("from-content");
        content.setConsumerType("user");
        content.setConsumerAmount(1);
        content.setNotBefore(new Date());
        content.setNotAfter(new Date(System.currentTimeMillis() + 86_400_000L));

        LicenseInfo info = LicenseInfo.from(content);

        assertThat(info).isNotNull();
        assertThat(info.getSubject()).isEqualTo("from-content");
        assertThat(info.getExtra()).isNull();
    }

    @Test
    void shouldReturnNullFromNullContent() {
        assertThat(LicenseInfo.from(null)).isNull();
    }

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

    @Test
    void extraModelShouldDetectConstraints() {
        LicenseExtraModel empty = new LicenseExtraModel();
        assertThat(empty.hasAnyConstraint()).isFalse();

        LicenseExtraModel withIp = new LicenseExtraModel();
        withIp.setIp("1.2.3.4");
        assertThat(withIp.hasAnyConstraint()).isTrue();
    }
}
