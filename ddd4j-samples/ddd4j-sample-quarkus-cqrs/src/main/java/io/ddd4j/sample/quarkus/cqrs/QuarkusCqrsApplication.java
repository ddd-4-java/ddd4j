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
package io.ddd4j.sample.quarkus.cqrs;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Quarkus + ddd4j Order/Goods CQRS 示例启动类。
 *
 * <p>本示例在统一的 ddd4j 业务框架下演示 <b>CQRS 命令/查询分离</b> 与 <b>双业务（Order + Goods）</b> 编排：
 * <ol>
 *   <li>ddd4j-runtime-quarkus 在启动期自动注入 4 个核心 SPI 到 {@code BaseContext}</li>
 *   <li>{@code @ApplicationScoped} 业务 Bean（Order / Goods 应用服务、仓储、缓存服务、事件观察者）由 CDI 容器托管</li>
 *   <li>JAX-RS 资源暴露 REST 端点（命令端 / 查询端分离）</li>
 *   <li>CDI {@code @Observes} 桥接 ddd4j 订单领域事件</li>
 *   <li>CacheKit 提供 Order / Goods 缓存（CQRS 读侧优先读取）</li>
 * </ol>
 *
 * <p>启动命令：{@code mvn quarkus:dev} 或 {@code mvn quarkus:build} 后运行打包产物。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@QuarkusMain
public class QuarkusCqrsApplication {

    public static void main(String[] args) {
        Quarkus.run(args);
    }
}