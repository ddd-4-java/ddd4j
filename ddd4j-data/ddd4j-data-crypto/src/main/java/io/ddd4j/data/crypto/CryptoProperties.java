package io.ddd4j.data.crypto;


import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.digest.HmacAlgorithm;
import io.ddd4j.data.crypto.domain.enums.CryptoType;
import io.ddd4j.data.crypto.domain.enums.SymmetricAlgorithmType;
import lombok.Data;

/**
 * 加密配置属性类
 * <p>用于配置加密解密方式、对称加密算法、密钥等参数</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class CryptoProperties {

    /**
     * 配置前缀
     */
    public static final String PREFIX = "crypto";

    /**
     * 加密解密方式，default:默认的行为，internal:内部加密，flksec:弗兰科信息
     *
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     */
    private CryptoType type = CryptoType.INTERNAL;

    /**
     * 对称加密算法
     */
    private SymmetricAlgorithmType symmetricAlgorithm = SymmetricAlgorithmType.SM4;

    /**
     * Hmac摘要算法
     */
    private HmacAlgorithm hmacAlgorithm = HmacAlgorithm.HmacSM3;

    /**
     * 模式
     * 加密算法模式，是用来描述加密算法（此处特指分组密码，不包括流密码，）在加密时对明文分组的模式，它代表了不同的分组方式，系统支持 ecb,cbc,cfb,ofb，推荐 cbc，国密不允许使用 ecb 模式
     */
    private Mode mode = Mode.CBC;

    /**
     * 补码方式：
     * 补码方式是在分组密码中，当明文长度不是分组长度的整数倍时，需要在最后一个分组中填充一些数据使其凑满一个分组的长度。
     */
    private Padding padding = Padding.PKCS5Padding;

    /**
     * 密钥，支持三种密钥长度：128、192、256位
     */
    private String key;

    /**
     * 偏移向量，加盐
     */
    private String iv;

    /**
     * 明文是否进行了 base64 编码 true/false
     */
    private boolean plainIsEncode;

    /**
     * 弗兰科信息加密服务地址
     */
    private String flksecAddress;
    private String flksecPort;

}
