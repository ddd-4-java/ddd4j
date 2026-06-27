package io.ddd4j.kit.crypto;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.digest.HMac;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HMAC 摘要签名算法工具类
 *
 * @author Loong Wan
 * @since 2.0.x
 */
@UtilityClass
public class HMACKit {

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
     * HMac-摘要
     */
    public static String digest(String algorithm, String salt, String plainTxt) {
        HMac hMac = getHMac(algorithm, salt);
        return hMac.digestHex(plainTxt);
    }

}
