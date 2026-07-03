/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webmvc.config;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地资源配置属性。
 * <p>用于配置本地静态资源的存储路径和映射关系。</p>
 */
@Data
public class LocalResourceProperteis {

    /** 本地存储路径 */
    private String localStorage;

    /** 本地静态资源映射是否是相对于 localStorage 的地址 */
    private boolean localRelative;

    /** 本地静态资源映射（key：访问路径，value：实际路径） */
    private Map<String, String> localLocations = new HashMap<>();

}
