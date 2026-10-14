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

import java.io.Serializable;
import java.util.Objects;

/**
 * 多租户复合主键值对象，由实体主键与租户标识组成，用于 {@link TenantAwareEntity} 等实体的 {@code @IdClass} 映射。
 * <p>
 * 对标 ddd4j-data 的 {@code BaseRepositoryImpl} 四泛型方案中的主键抽象。
 * </p>
 */
public class TenantAwareId implements Serializable {

    /** 实体主键。 */
    private Long id;
    /** 租户标识。 */
    private String tenantId;

    /**
     * 创建空的复合主键，供 JPA 使用。
     */
    public TenantAwareId() {
    }

    /**
     * 创建租户复合主键。
     *
     * @param id 实体主键
     * @param tenantId 租户标识
     */
    public TenantAwareId(Long id, String tenantId) {
        this.id = id;
        this.tenantId = tenantId;
    }

    /**
     * 创建租户复合主键。
     *
     * @param id 实体主键
     * @param tenantId 租户标识
     * @return 租户复合主键
     */
    public static TenantAwareId of(Long id, String tenantId) {
        return new TenantAwareId(id, tenantId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (Objects.isNull(o) || getClass() != o.getClass()) {
            return false;
        }
        TenantAwareId that = (TenantAwareId) o;
        return Objects.equals(id, that.id) && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId);
    }
}
