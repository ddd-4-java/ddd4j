package io.ddd4j.kit.crypto;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.digest.HMac;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HMAC 摘要签名算法工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public class HMACKit {

    /** \u6309\u76d0\u503c\u67e5\u627e\u548c\u590d\u7528 HMAC \u8fd0\u7b97\u5bf9\u8c61\u3002 */
    private final Map<String, HMac> hMacMap = new ConcurrentHashMap<>();

    /**
     * 获取 HMac
     *
     * @param algorithm 摘要算法 HmacMD5、HmacSHA1、HmacSHA256、HmacSHA384、HmacSHA512
     * @param salt      盐值
     * @return HMac
     */
    public static HMac getHMac(String algorithm, String salt) {
        return hMacMap.computeIfAbsent(salt, k -> new HMac(algorithm, salt.getBytes(CharsetUtil.CHARSET_UTF_8)));
    }

    /**
     * HMac-\u6458\u8981
     *
     * @param algorithm \u6458\u8981\u7b97\u6cd5
     * @param salt \u76d0\u503c
     * @param plainTxt \u5f85\u6458\u8981\u7684\u660e\u6587
     * @return \u5341\u516d\u8fdb\u5236\u6458\u8981
     */
    public static String digest(String algorithm, String salt, String plainTxt) {
        HMac hMac = getHMac(algorithm, salt);
        return hMac.digestHex(plainTxt);
    }

}
