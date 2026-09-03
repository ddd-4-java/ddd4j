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
package io.ddd4j.guice;

/**
 * Guice 运行时常量定义。
 *
 * <p>集中管理 Guice 模块绑定中的 {@code @Named} key 与默认值，
 * 避免魔法字面量散落在多个 Guice Module 类中。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public final class GuiceConstants {

    /**
     * ViewManager 调度线程池大小的 Guice {@code @Named} key。
     */
    public static final String VIEW_MANAGER_THREAD_POOL_SIZE_KEY = "ddd4j.view-manager.thread-pool-size";

    /**
     * ViewManager 调度线程池默认大小。
     */
    public static final int DEFAULT_THREAD_POOL_SIZE = 2;

    private GuiceConstants() {
    }
}
