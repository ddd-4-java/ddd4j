package io.ddd4j.extension.license.manager;

import de.schlichtherle.license.*;
import de.schlichtherle.xml.GenericCertificate;
import de.schlichtherle.xml.XMLConstants;
import io.ddd4j.extension.license.LicenseExtraModel;
import io.ddd4j.extension.license.machine.DefaultLicenseMachineInfoProvider;
import io.ddd4j.extension.license.machine.LicenseMachineInfoProvider;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.beans.XMLDecoder;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 自定义LicenseManager，用于增加额外的信息校验(除了LicenseManager的校验，我们还可以在这个类里面添加额外的校验信息)
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class CustomLicenseManager extends LicenseManager {

    private final LicenseMachineInfoProvider machineInfoProvider;

    public CustomLicenseManager(LicenseParam param) {
        this(param, DefaultLicenseMachineInfoProvider.INSTANCE);
    }

    public CustomLicenseManager(LicenseParam param, LicenseMachineInfoProvider machineInfoProvider) {
        super(param);
        this.machineInfoProvider = Objects.requireNonNull(machineInfoProvider, "machineInfoProvider 不能为空");
    }

    /**
     * 复写create方法
     */
    @Override
    protected synchronized byte[] create(LicenseContent content, LicenseNotary notary) throws Exception {
        initialize(content);
        this.validateCreate(content);
        final GenericCertificate certificate = notary.sign(content);
        return getPrivacyGuard().cert2key(certificate);
    }

    /**
     * 复写install方法，其中validate方法调用本类中的validate方法，校验IP地址、Mac地址等其他信息
     */
    @Override
    protected synchronized LicenseContent install(final byte[] key, final LicenseNotary notary) throws Exception {
        final GenericCertificate certificate = getPrivacyGuard().key2cert(key);
        notary.verify(certificate);
        final LicenseContent content = (LicenseContent) this.load(certificate.getEncoded());
        this.validate(content);
        setLicenseKey(key);
        setCertificate(certificate);

        return content;
    }

    /**
     * 复写verify方法，调用本类中的validate方法，校验IP地址、Mac地址等其他信息
     */
    @Override
    protected synchronized LicenseContent verify(final LicenseNotary notary) throws Exception {

        // Load license key from preferences,
        final byte[] key = getLicenseKey();
        if (Objects.isNull(key)) {
            throw new NoLicenseInstalledException(getLicenseParam().getSubject());
        }

        GenericCertificate certificate = getPrivacyGuard().key2cert(key);
        notary.verify(certificate);
        final LicenseContent content = (LicenseContent) this.load(certificate.getEncoded());
        this.validate(content);
        setCertificate(certificate);

        return content;
    }

    /**
     * 校验生成证书的参数信息
     *
     * @throws LicenseContentException 如果证书参数校验失败
     */
    protected synchronized void validateCreate(final LicenseContent content) throws LicenseContentException {
        final LicenseParam param = getLicenseParam();
        final Date now = new Date();
        final Date notBefore = content.getNotBefore();
        final Date notAfter = content.getNotAfter();
        if (Objects.nonNull(notAfter) && now.after(notAfter)) {
            throw new LicenseContentException("证书失效时间不能早于当前时间");
        }
        if (Objects.nonNull(notBefore) && Objects.nonNull(notAfter) && notAfter.before(notBefore)) {
            throw new LicenseContentException("证书生效时间不能晚于证书失效时间");
        }
        final String consumerType = content.getConsumerType();
        if (Objects.isNull(consumerType)) {
            throw new LicenseContentException("用户类型不能为空");
        }
    }


    /**
     * 复写 validate 方法，用于增加我们额外的校验信息。
     *
     * <p>校验顺序：
     * <ol>
     *   <li>父类 {@code validate}（subject、有效期等标准校验）；</li>
     *   <li>若证书声明了 {@link LicenseExtraModel}（IP/MAC/SN），与当前运行环境比对。</li>
     * </ol>
     * 向后兼容：extra 为 null 或所有约束字段为 null 时，跳过扩展校验。
     *
     * @throws LicenseContentException 如果自定义校验失败
     */
    @Override
    protected synchronized void validate(final LicenseContent content) throws LicenseContentException {
        //1. 首先调用父类的validate方法
        super.validate(content);
        //2. 然后校验自定义的License参数，去校验我们的license信息
        Object extra = content.getExtra();
        if (!(extra instanceof LicenseExtraModel expected)) {
            // 无扩展校验信息，直接通过（兼容旧证书）
            return;
        }
        validateExtra(expected);
    }

    /**
     * 校验缓存证书或原始证书中的机器约束。
     */
    public synchronized void validateExtra(LicenseExtraModel expected) throws LicenseContentException {
        if (Objects.isNull(expected) || !expected.hasAnyConstraint()) {
            return;
        }
        Set<String> ipAddresses = machineInfoProvider.ipAddresses();
        if (!matchesAny(expected.getIp(), ipAddresses, this::normalizeText)) {
            throw new LicenseContentException("IP 校验失败: 当前机器地址不在授权范围内");
        }
        Set<String> macAddresses = machineInfoProvider.macAddresses();
        if (!matchesAny(expected.getMac(), macAddresses, this::normalizeMac)) {
            throw new LicenseContentException("MAC 校验失败: 当前机器地址不在授权范围内");
        }
        String serialNumber = machineInfoProvider.serialNumber();
        if (Objects.nonNull(expected.getSn())
                && !matchesAny(expected.getSn(), Set.of(serialNumber), this::normalizeText)) {
            throw new LicenseContentException("SN 校验失败: 当前机器序列号不在授权范围内");
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


    /**
     * 重写XMLDecoder解析XML
     */
    private Object load(String encoded) {
        BufferedInputStream inputStream = null;
        XMLDecoder decoder = null;
        try {
            inputStream = new BufferedInputStream(new ByteArrayInputStream(encoded.getBytes(XMLConstants.XML_CHARSET)));
            decoder = new XMLDecoder(new BufferedInputStream(inputStream, XMLConstants.DEFAULT_BUFSIZE), null, null);
            return decoder.readObject();
        } catch (UnsupportedEncodingException e) {
            log.error("XMLDecoder解析XML编码失败", e);
        } finally {
            try {
                if (Objects.nonNull(decoder)) {
                    decoder.close();
                }
                if (Objects.nonNull(inputStream)) {
                    inputStream.close();
                }
            } catch (Exception e) {
                log.error("XMLDecoder解析XML失败", e);
            }
        }

        return null;
    }

}
