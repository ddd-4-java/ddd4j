/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    public static String g_charset = "ISO8859-1";

    /**
     * get token for file URL
     *
     * @param file_id    the file id return by FastDFS server
     * @param ts         unix timestamp, unit: second
     * @param secret_key the secret key
     * @return token string
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
     *
     * @param source the input buffer
     * @return md5 string
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
