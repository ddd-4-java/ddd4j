package io.ddd4j.data.crypto.util;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.digest.SM3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SM3 摘要签名算法工具类
 */
public class SM3Util {

    private static Map<String, SM3> sm3Map = new ConcurrentHashMap<>();

    /**
     * 获取SM3
     *
     * @param salt，加盐
     * @return SM3
     */
    public static SM3 getSm3(String salt) {
        return sm3Map.computeIfAbsent(salt, k -> new SM3(salt.getBytes(CharsetUtil.CHARSET_UTF_8)));
    }

    /**
     * SM3-摘要
     */
    public static String digest(String salt, String plainTxt) {
        SM3 sm3 = getSm3(salt);
        return sm3.digestHex(plainTxt);
    }

}
