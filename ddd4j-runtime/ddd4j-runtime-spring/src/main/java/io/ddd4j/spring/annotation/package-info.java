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
/**
 * ddd4j-runtime-spring DDD 注解包。
 *
 * <p>10 个 DDD 构造型注解的同名复制 + Spring 元注解融合实现。
 * 每个注解都是 {@link io.ddd4j.annotation.ddd.DDDAnnotation} 元注解标记，
 * 同时底层融合 Spring 的 {@code @Service} / {@code @Repository} / {@code @Component}。
 *
 * @see io.ddd4j.annotation.ddd.DDDAnnotation
 */
package io.ddd4j.spring.annotation;
