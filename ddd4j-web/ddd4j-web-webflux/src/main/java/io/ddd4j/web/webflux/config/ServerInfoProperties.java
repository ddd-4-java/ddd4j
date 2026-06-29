/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webflux.config;

import lombok.Data;

/**
 * 服务信息
 */
@Data
public class ServerInfoProperties {

    /**
     * 服务节点UID: spring-boot-admin 服务端注册ID
     */
    private String uid;
    /**
     * 服务节点名称
     */
    private String name;

    /**
     * 服务节点描述
     */
    private String description;

    /**
     * 服务节点版本
     */
    private String version;

    @Override
    public String toString() {
        return "ServiceInfo{" + "name='" + name + '\'' + ", version='" + version + '\'' + '}';
    }
}
