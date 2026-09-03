/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.data.external;

import lombok.Data;

/**
 * 外部服务配置属性类
 * <p>用于配置外部服务（如百度地图等）的访问密钥</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class ExternalProperties {

    /**
     * 配置前缀
     */
    public static final String PREFIX = "ddd4j.external";

    /**
     * 百度地图AK密钥
     */
    private String baiduAk;

}
