package io.ddd4j.data.crypto.util;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.digest.HMac;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HMAC 摘要签名算法工具类
 */
public class HMACUtil {

    private static Map<String, HMac> hMacMap = new ConcurrentHashMap<>();

    /**
     * 获取 HMac
     *
     * @param algorithm，摘要算法 HmacMD5、HmacSHA1、HmacSHA256、HmacSHA384、HmacSHA512
     * @param salt，加盐
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
