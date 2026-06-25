/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.web.webmvc.config;

import lombok.Data;

/**
 * 软件服务国际化信息
 */
@Data
public class ServerI18nProperties {

    /**
     * 是否启用国际化
     */
    private boolean enabled;
    /**
     * 根据环境是否给客户端抛出未知具体异常信息
     */
    private boolean printErrorDetail;

}
