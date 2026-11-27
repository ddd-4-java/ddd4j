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
     * 服务节点 UID
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

    /**
     * 生成用于调试和日志的对象描述，包含<code>name</code>、<code>version</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "ServiceInfo{" + "name='" + name + '\'' + ", version='" + version + '\'' + '}';
    }
}
