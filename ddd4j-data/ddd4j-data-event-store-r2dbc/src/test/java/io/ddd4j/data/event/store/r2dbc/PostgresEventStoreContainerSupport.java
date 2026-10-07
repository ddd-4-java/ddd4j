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
package io.ddd4j.data.event.store.r2dbc;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL 容器进程级单例夹具（同一 JVM 内全部 IT 共享一个容器，仅启动一次）。
 *
 * <p>Docker 不可用时保持未启动状态，由各 IT 用 {@code assumeTrue} 跳过。
 *
 * @since 1.0.x
 */
final class PostgresEventStoreContainerSupport {

    /**
     * 进程级单例 PostgreSQL 16 容器（官方 testcontainers PostgreSQL 模块镜像）。
     */
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    private PostgresEventStoreContainerSupport() {
    }
}
