package io.ddd4j.extension.qrcode.model;

import lombok.Value;

/**
 * 单个二维码解码结果。
 */
@Value
public class QrCodeDecodeResult {

    /** \u4ece\u8f93\u5165\u56fe\u50cf\u89e3\u7801\u51fa\u7684\u4e8c\u7ef4\u7801\u6587\u672c\u3002 */
    String text;
}
