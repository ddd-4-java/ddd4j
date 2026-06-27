package io.ddd4j.kit.crypto;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AES 对称加密工具类
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 * @since 2.0.x
 */
@UtilityClass
public class AESKit {

    private final Map<String, AES> aesMap = new ConcurrentHashMap<>();

    /**
     * 获取aes
     *
     * @param key 密钥，支持三种密钥长度：128、192、256位
     * @param iv  偏移向量，加盐
     * @return AES
     */
    public static AES getAes(String key, String iv) {
        return aesMap.computeIfAbsent(key + iv, k -> new AES(Mode.CBC, Padding.PKCS5Padding, key.getBytes(CharsetUtil.CHARSET_UTF_8),
                iv.getBytes(CharsetUtil.CHARSET_UTF_8)));
    }

    /**
     * AES-cbc加密
     */
    public static String encrypt(String key, String iv, String plainTxt) {
        SymmetricCrypto aes = getAes(key, iv);
        byte[] encrypHex = aes.encrypt(plainTxt);
        return Base64.encode(encrypHex);
    }

    /**
     * AES-cbc解密
     */
    public static String decrypt(String key, String iv, String cipherTxt) {
        SymmetricCrypto aes = getAes(key, iv);
        byte[] cipherHex = Base64.decode(cipherTxt.trim());
        return aes.decryptStr(cipherHex, CharsetUtil.CHARSET_UTF_8);
    }

}
