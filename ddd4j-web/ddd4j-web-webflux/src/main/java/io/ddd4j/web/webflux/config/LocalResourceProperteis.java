/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.web.webflux.config;

import com.google.common.collect.Maps;
import lombok.Data;

import java.util.Map;

@Data
public class LocalResourceProperteis {

    // 本地存储路径
    private String localStorage;

    // 本地静态资源映射是否是相对于localStorage的地址
    private boolean localRelative;

    // 本地静态资源映射
    private Map<String, String> localLocations = Maps.newHashMap();

}
