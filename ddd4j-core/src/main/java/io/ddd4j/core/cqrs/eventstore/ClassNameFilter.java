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

package io.ddd4j.core.cqrs.eventstore;

/**
 * 类名过滤 SPI：决定从不可信输入（事件存储）反序列化的类名是否允许加载。
 *
 * <p>用于加固 {@link EventDeserializer}：在 {@code Class.forName} 之前调用，
 * 业务方可通过实现该接口限制允许加载的根包前缀，超出范围的类名视为不可信，
 * 由 {@link EventDeserializer} 回退为 {@code Map} 反序列化（保留数据可读性）。
 *
 * <h3>实现约定</h3>
 * <ul>
 *   <li>实现必须是线程安全的——{@link EventDeserializer} 在反序列化热路径调用</li>
 *   <li>不允许返回 {@code null}；拒绝类名时返回 {@code false}</li>
 *   <li>默认实现见 {@link EventDeserializer#defaultFilter()}</li>
 * </ul>
 *
 * <h3>注册方式</h3>
 * <p>通过 {@link EventDeserializer#setFilter(ClassNameFilter)} 设置进程级过滤器；
 * 单 JVM 仅允许一个过滤器，后注册覆盖前注册。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
@FunctionalInterface
public interface ClassNameFilter {
    /**
     * 判断类名是否允许加载。
     *
     * @param className 类全限定名（已经过 {@link EventDeserializer#isValidClassName} 校验为合法格式）
     * @return 允许时 {@code true}；不允许时 {@code false}
     */
    boolean allows(String className);
}
