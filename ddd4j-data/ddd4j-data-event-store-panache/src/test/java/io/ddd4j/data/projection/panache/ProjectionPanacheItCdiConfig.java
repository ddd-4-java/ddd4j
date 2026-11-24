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
package io.ddd4j.data.projection.panache;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * 集成测试 CDI 装配（Task 7.3，H2 内存库 @QuarkusTest 轨专用）。
 *
 * <p>受测的 {@link QuarkusProjectionPositionRepository} 无协作者 Bean（active record
 * 静态委托，见其 javadoc），无需生产者装配；本类仅提供用例间隔离原语：
 * 每用例前清空 {@code ddd4j_projection_position}。写操作需活动事务，
 * 故以 {@code @Transactional} 包装（与 -panache 事件存储模块的
 * {@code PanacheItCdiConfig} 同款模式）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@ApplicationScoped
class ProjectionPanacheItCdiConfig {

    /**
     * 清空投影位置表（用例间隔离）。
     */
    @Transactional
    void clearPositions() {
        PanacheProjectionPositionEntity.deleteAll();
    }
}
