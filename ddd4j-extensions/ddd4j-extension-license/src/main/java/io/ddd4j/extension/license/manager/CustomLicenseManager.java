package io.ddd4j.extension.license.manager;

import de.schlichtherle.license.*;
import de.schlichtherle.xml.GenericCertificate;
import de.schlichtherle.xml.XMLConstants;
import io.ddd4j.extension.license.LicenseExtraModel;
import lombok.extern.slf4j.Slf4j;

import java.beans.XMLDecoder;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Objects;

/**
 * 自定义LicenseManager，用于增加额外的信息校验(除了LicenseManager的校验，我们还可以在这个类里面添加额外的校验信息)
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class CustomLicenseManager extends LicenseManager {

    public CustomLicenseManager(LicenseParam param) {
        super(param);
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
        if (!(extra instanceof LicenseExtraModel)) {
            // 无扩展校验信息，直接通过（兼容旧证书）
            return;
        }
        LicenseExtraModel expected = (LicenseExtraModel) extra;
        if (!expected.hasAnyConstraint()) {
            return;
        }
        // 做我们自定义的校验：IP / MAC / SN
        if (Objects.nonNull(expected.getIp())) {
            String actualIp = currentIp();
            if (!Objects.equals(expected.getIp(), actualIp)) {
                throw new LicenseContentException(
                        "IP 校验失败: 期望=" + expected.getIp() + ", 实际=" + actualIp);
            }
        }
        if (Objects.nonNull(expected.getMac())) {
            String actualMac = currentMac();
            if (!Objects.equals(expected.getMac(), actualMac)) {
                throw new LicenseContentException(
                        "MAC 校验失败: 期望=" + expected.getMac() + ", 实际=" + actualMac);
            }
        }
        if (Objects.nonNull(expected.getSn())) {
            String actualSn = currentSn();
            if (!Objects.equals(expected.getSn(), actualSn)) {
                throw new LicenseContentException(
                        "SN 校验失败: 期望=" + expected.getSn() + ", 实际=" + actualSn);
            }
        }
    }

    /**
     * 获取当前机器首选 IP（非回环）。
     *
     * <p>简化实现：取首个非回环 IPv4。失败返回 null（调用方会因不匹配而拒绝）。
     */
    private String currentIp() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("获取本机 IP 失败", e);
            return null;
        }
    }

    /**
     * 获取当前机器首选 MAC 地址。
     *
     * <p>简化实现：取首个非回环网卡的硬件地址。失败返回 null。
     */
    private String currentMac() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> nics = java.net.NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                java.net.NetworkInterface nic = nics.nextElement();
                if (nic.isLoopback() || !nic.isUp()) {
                    continue;
                }
                byte[] mac = nic.getHardwareAddress();
                if (Objects.isNull(mac) || mac.length == 0) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < mac.length; i++) {
                    sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : ""));
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.warn("获取本机 MAC 失败", e);
        }
        return null;
    }

    /**
     * 获取当前机器 SN 序列号。
     *
     * <p>简化实现：使用 OS name + user.name 拼装的稳定标识。生产可替换为读取真实硬件 SN。
     */
    private String currentSn() {
        return System.getProperty("os.name") + ":" + System.getProperty("user.name");
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
