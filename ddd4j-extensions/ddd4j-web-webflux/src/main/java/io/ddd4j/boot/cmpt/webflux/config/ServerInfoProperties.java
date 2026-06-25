/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.boot.cmpt.webflux.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务信息
 */
@ConfigurationProperties("server.info")
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
