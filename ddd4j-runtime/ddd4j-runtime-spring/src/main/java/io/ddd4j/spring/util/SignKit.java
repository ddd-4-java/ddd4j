package io.ddd4j.spring.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * 签名工具类。
 * <p>
 * 提供基于 MD5 的 API 签名/验签能力，支持 token、表单参数、请求体等参与签名计算。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class SignKit {

    /**
     * 1\u3001\u83b7\u53d6\u5bc6\u94a5
     * @param appId \u5ba2\u6237\u7aefid
     * @param appVersion \u7248\u672c\u53f7
     * @param appChannel \u6e20\u9053
     * @param fixedSecret \u56fa\u5b9a\u5bc6\u94a5
     * @return \u5bc6\u94a5
     */
    public static String salt(String appId, String appVersion, String appChannel, String fixedSecret) {
        String originStr = appId + appVersion + appChannel + fixedSecret;
        String salt = DigestUtils.md5DigestAsHex(originStr.getBytes()).substring(7, 23);
        log.info("salt : {}", salt);
        return salt;
    }

    /**
     * 2、验签
     *
     * @param token    token
     * @param formData 参数
     * @param body     方法体
     * @param salt     密钥
     * @return 签名原文
     * @throws UnsupportedEncodingException
     */
    protected static String signOrigin(String token, MultiValueMap<String, String> formData, String body, String salt) throws UnsupportedEncodingException {

        StringBuilder requestData = new StringBuilder(StringUtils.defaultString(token)).append("|");
        // form参数
        if (MapUtils.isEmpty(formData)) {
            requestData.append("|");
        } else {

            TreeMap<String, String> params = new TreeMap<>(Comparator.naturalOrder());
            formData.forEach((key, value) -> {
                if (value.isEmpty()) {
                    params.put(key, "");
                } else {
                    params.put(key, value.get(0));
                }
            });

            params.forEach((key, value) -> {
                if (StringUtils.isEmpty(value)) {
                    requestData.append("|");
                } else {
                    requestData.append(value).append("|");
                }
            });
        }
        // body参数
        if (StringUtils.isEmpty(body)) {
            requestData.append("{}");
        } else {
            requestData.append(StringUtils.deleteWhitespace(body));
        }

        String signOrigin = URLEncoder.encode(requestData.toString(), StandardCharsets.UTF_8.name());

        return DigestUtils.md5DigestAsHex(signOrigin.getBytes());
    }

    /**
     * \u5bf9\u591a\u503c\u8868\u5355\u8bf7\u6c42\u8ba1\u7b97\u7b7e\u540d\u3002
     * @param token \u8bf7\u6c42\u4ee4\u724c
     * @param formData \u8868\u5355\u53c2\u6570
     * @param body \u8bf7\u6c42\u4f53
     * @param salt \u7b7e\u540d\u76d0\u503c
     * @return \u8bf7\u6c42\u7b7e\u540d
     * @throws UnsupportedEncodingException \u8bf7\u6c42\u53c2\u6570\u7f16\u7801\u5931\u8d25\u65f6\u629b\u51fa
     */
    public static String sign(String token, MultiValueMap<String, String> formData, String body, String salt) throws UnsupportedEncodingException {

        String originSign = signOrigin(token, formData, body, salt);

        // 在originSign字符串中每隔两个位置插入一个salt
        char[] cs = new char[48];
        for (int i = 0; i < 48; i += 3) {
            cs[i] = originSign.charAt(i / 3 * 2);
            char c = salt.charAt(i / 3);
            cs[i + 1] = c;
            cs[i + 2] = originSign.charAt(i / 3 * 2 + 1);
        }
        String sign = new String(cs);

        return sign;

    }

    /**
     * \u6821\u9a8c\u591a\u503c\u8868\u5355\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u7b7e\u540d\u3002
     * @param token \u8bf7\u6c42\u4ee4\u724c
     * @param appId \u5e94\u7528\u6807\u8bc6
     * @param appVersion \u5e94\u7528\u7248\u672c
     * @param appChannel \u5e94\u7528\u6e20\u9053
     * @param fixedSecret \u56fa\u5b9a\u5bc6\u94a5
     * @param clientSign \u5ba2\u6237\u7aef\u7b7e\u540d
     * @param params \u8868\u5355\u53c2\u6570
     * @param body \u8bf7\u6c42\u4f53
     * @return \u7b7e\u540d\u662f\u5426\u5339\u914d
     * @throws UnsupportedEncodingException \u8bf7\u6c42\u53c2\u6570\u7f16\u7801\u5931\u8d25\u65f6\u629b\u51fa
     */
    public static boolean verify(String token, String appId, String appVersion, String appChannel,
                                 String fixedSecret, String clientSign, MultiValueMap<String, String> params, String body)
            throws UnsupportedEncodingException {

        String salt = salt(appId, appVersion, appChannel, fixedSecret);

        String sign = sign(token, params, body, salt);

        return sign.equals(clientSign);
    }

    /**
     * 2、验签
     *
     * @param token    token
     * @param formData 参数
     * @param body     方法体
     * @param salt     密钥
     * @return 签名原文
     * @throws UnsupportedEncodingException
     */
    protected static String signOrigin(String token, Map<String, String[]> formData, String body, String salt) throws UnsupportedEncodingException {

        StringBuilder requestData = new StringBuilder(StringUtils.defaultString(token)).append("|");
        // form参数
        if (MapUtils.isEmpty(formData)) {
            requestData.append("|");
        } else {

            TreeMap<String, String> params = new TreeMap<>(Comparator.naturalOrder());
            formData.forEach((key, values) -> {
                if (values.length == 0) {
                    params.put(key, "");
                } else {
                    params.put(key, values[0]);
                }
            });

            params.forEach((key, value) -> {
                if (StringUtils.isEmpty(value)) {
                    requestData.append("|");
                } else {
                    requestData.append(value).append("|");
                }
            });
        }
        // body参数
        if (StringUtils.isEmpty(body)) {
            requestData.append("{}");
        } else {
            requestData.append(StringUtils.deleteWhitespace(body));
        }

        String signOrigin = URLEncoder.encode(requestData.toString(), StandardCharsets.UTF_8.name());

        return DigestUtils.md5DigestAsHex(signOrigin.getBytes());
    }

    /**
     * \u5bf9\u6570\u7ec4\u503c\u8868\u5355\u8bf7\u6c42\u8ba1\u7b97\u7b7e\u540d\u3002
     * @param token \u8bf7\u6c42\u4ee4\u724c
     * @param formData \u8868\u5355\u53c2\u6570
     * @param body \u8bf7\u6c42\u4f53
     * @param salt \u7b7e\u540d\u76d0\u503c
     * @return \u8bf7\u6c42\u7b7e\u540d
     * @throws UnsupportedEncodingException \u8bf7\u6c42\u53c2\u6570\u7f16\u7801\u5931\u8d25\u65f6\u629b\u51fa
     */
    public static String sign(String token, Map<String, String[]> formData, String body, String salt) throws UnsupportedEncodingException {

        String originSign = signOrigin(token, formData, body, salt);

        // 在originSign字符串中每隔两个位置插入一个salt
        char[] cs = new char[48];
        for (int i = 0; i < 48; i += 3) {
            cs[i] = originSign.charAt(i / 3 * 2);
            char c = salt.charAt(i / 3);
            cs[i + 1] = c;
            cs[i + 2] = originSign.charAt(i / 3 * 2 + 1);
        }
        String sign = new String(cs);

        return sign;

    }

    /**
     * \u6821\u9a8c\u6570\u7ec4\u503c\u8868\u5355\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u7b7e\u540d\u3002
     * @param token \u8bf7\u6c42\u4ee4\u724c
     * @param appId \u5e94\u7528\u6807\u8bc6
     * @param appVersion \u5e94\u7528\u7248\u672c
     * @param appChannel \u5e94\u7528\u6e20\u9053
     * @param fixedSecret \u56fa\u5b9a\u5bc6\u94a5
     * @param clientSign \u5ba2\u6237\u7aef\u7b7e\u540d
     * @param formData \u8868\u5355\u53c2\u6570
     * @param body \u8bf7\u6c42\u4f53
     * @return \u7b7e\u540d\u662f\u5426\u5339\u914d
     * @throws UnsupportedEncodingException \u8bf7\u6c42\u53c2\u6570\u7f16\u7801\u5931\u8d25\u65f6\u629b\u51fa
     */
    public static boolean verify(String token, String appId, String appVersion, String appChannel,
                                 String fixedSecret, String clientSign, Map<String, String[]> formData, String body)
            throws UnsupportedEncodingException {

        String salt = salt(appId, appVersion, appChannel, fixedSecret);

        String sign = sign(token, formData, body, salt);

        return sign.equals(clientSign);
    }

    /**
     * \u8f93\u51fa\u7b7e\u540d\u4e0e\u6821\u9a8c\u793a\u4f8b\u3002
     * @param args \u547d\u4ee4\u884c\u53c2\u6570
     * @throws Exception \u793a\u4f8b\u6267\u884c\u5931\u8d25\u65f6\u629b\u51fa
     */
    public static void main(String[] args) throws Exception {

        String appId = "1", appVersion = "20000", appChannel = "ASO000", fixedSecret = "z2023";

        String salt = SignKit.salt(appId, appVersion, appChannel, fixedSecret);
        log.info("salt={}", salt);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        String token = "";
        String body = "";

        String sign = SignKit.sign(token, formData, body, salt);

        log.info("sign={}", sign);

        String clientSign = "2136d6ec5e2c17b3ac787bf6f86fffe7ab7b0c389efa970a";

        log.info("verified={}", SignKit.verify(token, appId, appVersion, appChannel, fixedSecret, clientSign, formData, body));

    }


}
