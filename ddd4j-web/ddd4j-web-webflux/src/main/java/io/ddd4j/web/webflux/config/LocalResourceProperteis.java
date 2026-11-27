/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webflux.config;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/** \u672c\u5730\u9759\u6001\u8d44\u6e90\u6620\u5c04\u914d\u7f6e\u5c5e\u6027\u3002 */
@Data
public class LocalResourceProperteis {

    // 本地存储路径
    /** \u672c\u5730\u9759\u6001\u8d44\u6e90\u76ee\u5f55\u3002 */
    private String localStorage;

    // 本地静态资源映射是否是相对于localStorage的地址
    /** \u9759\u6001\u8d44\u6e90\u76ee\u5f55\u662f\u5426\u76f8\u5bf9\u4e8e\u672c\u5730\u5b58\u50a8\u8def\u5f84\u89e3\u6790\u3002 */
    private boolean localRelative;

    // 本地静态资源映射
    /** URL \u8def\u5f84\u6a21\u5f0f\u4e0e\u672c\u5730\u9759\u6001\u8d44\u6e90\u76ee\u5f55\u7684\u6620\u5c04\u3002 */
    private Map<String, String> localLocations = new HashMap<>();

}
