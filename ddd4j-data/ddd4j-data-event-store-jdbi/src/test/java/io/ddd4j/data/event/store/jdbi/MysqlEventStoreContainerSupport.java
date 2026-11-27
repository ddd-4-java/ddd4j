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
package io.ddd4j.data.event.store.jdbi;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;

/**
 * MySQL 容器进程级单例夹具（同一 JVM 内全部 IT 共享一个容器，仅启动一次）。
 *
 * <p>Docker 不可用时保持未启动状态，由各 IT 用 {@code assumeTrue} 跳过。
 *
 * @since 1.0.x
 */
final class MysqlEventStoreContainerSupport {

    /**
     * 进程级单例 MySQL 8 容器（官方 testcontainers MySQL 模块镜像）。
     */
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withTmpFs(Collections.singletonMap("/var/lib/mysql", "rw"))
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("useSSL", "false");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            MYSQL.start();
        }
    }

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>MysqlEventStoreContainerSupport</code> \u5b9e\u4f8b\u3002 */
    private MysqlEventStoreContainerSupport() {
    }
}
