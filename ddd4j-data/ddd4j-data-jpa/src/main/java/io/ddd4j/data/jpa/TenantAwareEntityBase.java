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
package io.ddd4j.data.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 标准 Jakarta Persistence 审计实体基类。
 *
 * <p>本类型不依赖 Hibernate 或 Quarkus Panache。查询、分页与租户过滤由
 * Repository 适配器负责，持久化对象只承载映射和审计状态。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@MappedSuperclass
public abstract class TenantAwareEntityBase {

    /** \u521b\u5efa\u65f6\u95f4\u3002 */
    @Column(name = "created_time", updatable = false)
    protected LocalDateTime createdTime;

    /** \u6700\u540e\u66f4\u65b0\u65f6\u95f4\u3002 */
    @Column(name = "updated_time")
    protected LocalDateTime updatedTime;

    /** \u9996\u6b21\u6301\u4e45\u5316\u65f6\u5199\u5165\u5ba1\u8ba1\u65f6\u95f4\u3002 */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(createdTime)) {
            createdTime = now;
        }
        updatedTime = now;
    }

    /** \u66f4\u65b0\u5b9e\u4f53\u65f6\u5237\u65b0\u5ba1\u8ba1\u65f6\u95f4\u3002 */
    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }

    /**
     * \u8fd4\u56de\u521b\u5efa\u65f6\u95f4\u3002
     * @return \u521b\u5efa\u65f6\u95f4
     */
    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    /**
     * \u8fd4\u56de\u6700\u540e\u66f4\u65b0\u65f6\u95f4\u3002
     * @return \u6700\u540e\u66f4\u65b0\u65f6\u95f4
     */
    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }
}
