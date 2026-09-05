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
package io.ddd4j.quarkus.event;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Quarkus 事件存储配置提示器。
 * <p>
 * <p>事件存储由应用通过 ddd4j 的 {@code EventChunkReader} / {@code ProjectionService}
 * 端口显式接入。本类不再为未知生产配置隐式创建内存事件库，避免事件在重启后静默丢失。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@ApplicationScoped
public class Ddd4jEventStoreConfig {

    /**
     * EventStore 类型配置
     */
    @Inject
    @ConfigProperty(name = "ddd4j.ddd.event-store.type", defaultValue = "mem")
    String eventStoreType;

    void onStart(@Observes StartupEvent event) {
        log.info("ddd4j event-store mode is '{}'; applications must register an explicit event-store adapter", eventStoreType);
    }
}
