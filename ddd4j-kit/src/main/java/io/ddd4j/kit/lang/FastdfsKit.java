/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.kit.lang;

import java.security.MessageDigest;

/**
 * FastDFS 文件存储 Token 工具。
 *
 * <p>提供 FastDFS 文件 URL 的 Token 生成与校验能力。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
public class FastdfsKit {

    /** FastDFS \u5730\u5740\u7f16\u7801\u4f7f\u7528\u7684\u5b57\u7b26\u96c6\u3002 */
    public static String g_charset = "ISO8859-1";

    /**
     * get token for file URL
     * @param file_id the file id return by FastDFS server
     * @param ts unix timestamp, unit: second
     * @param secret_key the secret key
     * @return token string
     *
     * @throws Exception \u8ba1\u7b97\u4e0b\u8f7d token \u5931\u8d25
     */
    public static String getToken(String file_id, long ts, String secret_key) throws Exception {
        byte[] bsFileId = file_id.getBytes(g_charset);
        byte[] bsKey = secret_key.getBytes(g_charset);
        byte[] bsTimestamp = Long.valueOf(ts).toString().getBytes(g_charset);

        byte[] buff = new byte[bsFileId.length + bsKey.length + bsTimestamp.length];
        System.arraycopy(bsFileId, 0, buff, 0, bsFileId.length);
        System.arraycopy(bsKey, 0, buff, bsFileId.length, bsKey.length);
        System.arraycopy(bsTimestamp, 0, buff, bsFileId.length + bsKey.length, bsTimestamp.length);

        return md5(buff);
    }

    /**
     * md5 function
     * @param source the input buffer
     * @return md5 string
     *
     * @throws java.security.NoSuchAlgorithmException \u6458\u8981\u7b97\u6cd5\u4e0d\u53ef\u7528
     */
    public static String md5(byte[] source) throws java.security.NoSuchAlgorithmException {
        char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(source);
        byte[] tmp = md.digest();
        char[] str = new char[32];
        int k = 0;
        for (int i = 0; i < 16; i++) {
            str[k++] = hexDigits[tmp[i] >>> 4 & 0xf];
            str[k++] = hexDigits[tmp[i] & 0xf];
        }

        return new String(str);
    }

}
