package io.ddd4j.kit.crypto;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.digest.SM3;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SM3 摘要签名算法工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public class SM3Kit {

    /** \u6309\u76d0\u503c\u67e5\u627e\u548c\u590d\u7528 SM3 \u6458\u8981\u5bf9\u8c61\u3002 */
    private final Map<String, SM3> sm3Map = new ConcurrentHashMap<>();

    /**
     * 获取SM3
     *
     * @param salt 盐值
     * @return SM3
     */
    public static SM3 getSm3(String salt) {
        return sm3Map.computeIfAbsent(salt, k -> new SM3(salt.getBytes(CharsetUtil.CHARSET_UTF_8)));
    }

    /**
     * SM3-\u6458\u8981
     *
     * @param salt \u76d0\u503c
     * @param plainTxt \u5f85\u6458\u8981\u7684\u660e\u6587
     * @return \u5341\u516d\u8fdb\u5236\u6458\u8981
     */
    public static String digest(String salt, String plainTxt) {
        SM3 sm3 = getSm3(salt);
        return sm3.digestHex(plainTxt);
    }

}
