package io.ddd4j.extension.license.creator;

import io.ddd4j.extension.license.LicenseInfo;
import io.ddd4j.extension.license.SignedLicense;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Date;
import java.util.Objects;

/**
 * 使用 JDK KeyStore 与签名 API 签发 ddd4j 许可证。
 */
@Slf4j
public class LicenseCreator {

    private final LicenseCreatorParam param;

    /**
     * 创建许可证签发器。
     *
     * @param param 签发参数
     */
    public LicenseCreator(LicenseCreatorParam param) {
        this.param = param;
    }

    /**
     * 生成签名许可证。
     *
     * @return 生成成功返回 {@code true}
     */
    public boolean generateLicense() {
        validateParam();
        if (Objects.isNull(param.getExpiryTime())) {
            param.setExpiryTime(new Date(System.currentTimeMillis() + 365L * 24L * 60L * 60L * 1000L));
            log.warn("未指定 expiryTime，默认设为 1 年后");
        }
        Path target = Paths.get(param.getLicensePath()).toAbsolutePath().normalize();
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            LicenseInfo licenseInfo = createLicenseInfo();
            byte[] payload = JsonKit.toJson(licenseInfo).getBytes(StandardCharsets.UTF_8);
            PrivateKey privateKey = loadPrivateKey();
            String algorithm = signatureAlgorithm(privateKey.getAlgorithm());
            Signature signature = Signature.getInstance(algorithm);
            signature.initSign(privateKey);
            signature.update(payload);
            SignedLicense license = new SignedLicense(algorithm, payload, signature.sign());

            temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            Files.write(temporary, license.serialize().getBytes(StandardCharsets.UTF_8));
            moveAtomically(temporary, target);
            temporary = null;
            log.info("License 证书生成成功: path={}, subject={}", target, param.getSubject());
            return true;
        } catch (Exception exception) {
            log.error("License 证书生成失败: subject={}, path={}", param.getSubject(), target, exception);
            return false;
        } finally {
            if (Objects.nonNull(temporary)) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception exception) {
                    log.warn("清理 License 临时文件失败: path={}", temporary, exception);
                }
            }
        }
    }

    private LicenseInfo createLicenseInfo() {
        return LicenseInfo.builder()
                .subject(param.getSubject())
                .issued(param.getIssuedTime())
                .notBefore(param.getIssuedTime())
                .notAfter(param.getExpiryTime())
                .consumerType(param.getConsumerType())
                .consumerAmount(param.getConsumerAmount())
                .extra(param.getLicenseExtraModel())
                .build();
    }

    private PrivateKey loadPrivateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (java.io.InputStream inputStream = Files.newInputStream(Paths.get(param.getPrivateKeysStorePath()))) {
            keyStore.load(inputStream, param.getStorePass().toCharArray());
        }
        Key key = keyStore.getKey(param.getPrivateAlias(), param.getKeyPass().toCharArray());
        if (!(key instanceof PrivateKey)) {
            throw new IllegalStateException("私钥别称未指向 PrivateKey: " + param.getPrivateAlias());
        }
        PrivateKey privateKey = (PrivateKey) key;
        return privateKey;
    }

    private void validateParam() {
        Objects.requireNonNull(param, "LicenseCreatorParam 不能为空");
        requireText(param.getSubject(), "subject");
        requireText(param.getPrivateAlias(), "privateAlias");
        requireText(param.getKeyPass(), "keyPass");
        requireText(param.getStorePass(), "storePass");
        requireText(param.getPrivateKeysStorePath(), "privateKeysStorePath");
        requireText(param.getLicensePath(), "licensePath");
        requireText(param.getConsumerType(), "consumerType");
        if (!Files.isReadable(Paths.get(param.getPrivateKeysStorePath()))) {
            throw new IllegalStateException("私钥库不存在或不可读: " + param.getPrivateKeysStorePath());
        }
        if (Objects.isNull(param.getConsumerAmount()) || param.getConsumerAmount() <= 0) {
            throw new IllegalStateException("consumerAmount 必须大于 0");
        }
        if (Objects.nonNull(param.getExpiryTime()) && Objects.nonNull(param.getIssuedTime())
                && !param.getExpiryTime().after(param.getIssuedTime())) {
            throw new IllegalStateException("expiryTime 必须晚于 issuedTime");
        }
    }

    private void requireText(String value, String field) {
        if (StrKit.isBlank(value)) {
            throw new IllegalStateException("生成证书缺少必填参数: " + field);
        }
    }

    private void moveAtomically(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String signatureAlgorithm(String keyAlgorithm) {
        if ("EC".equalsIgnoreCase(keyAlgorithm)) {
            return "SHA256withECDSA";
        }
        if ("DSA".equalsIgnoreCase(keyAlgorithm)) {
            return "SHA256withDSA";
        }
        if ("RSA".equalsIgnoreCase(keyAlgorithm)) {
            return "SHA256withRSA";
        }
        throw new IllegalStateException("不支持的私钥算法: " + keyAlgorithm);
    }

}
