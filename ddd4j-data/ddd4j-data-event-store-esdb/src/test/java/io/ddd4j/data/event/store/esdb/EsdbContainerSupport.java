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
package io.ddd4j.data.event.store.esdb;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * EventStoreDB 容器进程级单例夹具（同一 JVM 内全部 IT 共享一个容器，仅启动一次）。
 *
 * <p>ESDB 无官方 testcontainers 模块，使用 {@code GenericContainer} + 官方
 * {@code eventstore/eventstore} 镜像。Docker 不可用时保持未启动状态，由各 IT 跳过。
 *
 * @since 1.0.x
 */
final class EsdbContainerSupport {

    /**
     * 进程级单例 EventStoreDB 容器（单节点、内存库、 insecure 模式）。
     */
    static final GenericContainer<?> ESDB = new GenericContainer<>(
            DockerImageName.parse("eventstore/eventstore:24.10.0-bookworm-slim"))
            .withExposedPorts(2113)
            .withEnv("EVENTSTORE_CLUSTER_SIZE", "1")
            .withEnv("EVENTSTORE_RUN_PROJECTIONS", "All")
            .withEnv("EVENTSTORE_START_STANDARD_PROJECTIONS", "true")
            .withEnv("EVENTSTORE_INSECURE", "true")
            .withEnv("EVENTSTORE_MEM_DB", "true")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            ESDB.start();
        }
    }

    private EsdbContainerSupport() {
    }
}
