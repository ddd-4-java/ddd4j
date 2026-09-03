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
package io.ddd4j.core.health;

/**
 * 应用是否可接收业务流量的框架无关检查 SPI。
 *
 * <p>数据库、缓存和消息中间件等基础设施由应用按需实现本接口。Runtime 仅负责把
 * {@link ReadinessReport} 映射到各自的健康检查端点，不向核心层泄漏具体连接客户端。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface ReadinessContributor {

    /**
     * 执行一次就绪检查。
     *
     * @return 当前依赖的就绪结果
     */
    ReadinessResult check();
}
