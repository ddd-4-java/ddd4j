package io.ddd4j.extension.license.creator;

import de.schlichtherle.license.*;
import io.ddd4j.extension.license.CustomKeyStoreParam;
import io.ddd4j.extension.license.manager.CustomLicenseManager;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * License生成类 -- 用于license生成
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class LicenseCreator {

    private final static X500Principal DEFAULT_HOLDER_AND_ISSUER = new X500Principal("CN=localhost, OU=localhost, O=localhost, L=SH, ST=SH, C=CN");

    /**
     * License 生成参数
     */
    private final LicenseCreatorParam param;

    /**
     * 构造函数
     *
     * @param param License 生成参数
     */
    public LicenseCreator(LicenseCreatorParam param) {
        this.param = param;
    }

    /**
     * 生成License证书
     *
     * @return true 表示生成成功
     * @throws IllegalStateException 当 {@code param} 或必填项为空时抛出
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
            temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            LicenseManager licenseManager = new CustomLicenseManager(initLicenseParam());
            LicenseContent licenseContent = initLicenseContent();
            licenseManager.store(licenseContent, temporary.toFile());
            moveAtomically(temporary, target);
            temporary = null;
            log.info("License 证书生成成功: path={}, subject={}", target, param.getSubject());
            return true;
        } catch (Exception e) {
            log.error("License 证书生成失败: subject={}, path={}", param.getSubject(), target, e);
            return false;
        } finally {
            if (Objects.nonNull(temporary)) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception e) {
                    log.warn("清理 License 临时文件失败: path={}", temporary, e);
                }
            }
        }
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
        File keyStore = new File(param.getPrivateKeysStorePath());
        if (!keyStore.isFile() || !keyStore.canRead()) {
            throw new IllegalStateException("私钥库不存在或不可读: " + keyStore.getAbsolutePath());
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
        if (Objects.isNull(value) || StrKit.isEmpty(value.trim())) {
            throw new IllegalStateException("生成证书缺少必填参数: " + field);
        }
    }

    private void moveAtomically(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 初始化证书生成参数
     */
    private LicenseParam initLicenseParam() {
        // 获取preferences
        Preferences preferences = Preferences.userNodeForPackage(LicenseCreator.class);
        //设置对证书内容加密的秘钥
        CipherParam cipherParam = new DefaultCipherParam(param.getStorePass());
        // 设置私钥库
        KeyStoreParam privateStoreParam = new CustomKeyStoreParam(LicenseCreator.class
                , param.getPrivateKeysStorePath()
                , param.getPrivateAlias()
                , param.getStorePass()
                , param.getKeyPass());
        // 初始化证书生成参数
        return new DefaultLicenseParam(param.getSubject()
                , preferences
                , privateStoreParam
                , cipherParam);
    }

    /**
     * 设置证书生成正文信息
     */
    private LicenseContent initLicenseContent() {
        // 创建证书内容
        LicenseContent licenseContent = new LicenseContent();
        // 设置证书内容的发行者和发行者
        licenseContent.setHolder(DEFAULT_HOLDER_AND_ISSUER);
        licenseContent.setIssuer(DEFAULT_HOLDER_AND_ISSUER);
        //
        // 设置证书内容的主体、发行时间、生效时间、过期时间、消费者类型、消费者数量、信息
        licenseContent.setSubject(param.getSubject());
        licenseContent.setIssued(param.getIssuedTime());
        licenseContent.setNotBefore(param.getIssuedTime());
        licenseContent.setNotAfter(param.getExpiryTime());
        licenseContent.setConsumerType(param.getConsumerType());
        licenseContent.setConsumerAmount(param.getConsumerAmount());
        licenseContent.setInfo(param.getDescription());
        // 设置证书内容的扩展信息
        licenseContent.setInfo(param.getDescription());
        // 这里可以自定义一些额外的校验信息(也可以用json字符串保存)
        if (Objects.nonNull(param.getLicenseExtraModel())) {
            licenseContent.setExtra(param.getLicenseExtraModel());
        }

        return licenseContent;
    }

}
