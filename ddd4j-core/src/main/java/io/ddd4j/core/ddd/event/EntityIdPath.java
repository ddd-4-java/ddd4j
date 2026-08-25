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
package io.ddd4j.core.ddd.event;

import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * 从聚合根到事件源实体的有序标识路径。
 */
public final class EntityIdPath implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 路径分隔符。
     */
    public static final String PATH_SEPARATOR = "/";

    private final List<EntityId> entityIds;

    /**
     * 使用有序标识创建路径。
     *
     * @param entityIds 从外到内的实体标识
     */
    public EntityIdPath(EntityId... entityIds) {
        this(Arrays.asList(Objects.requireNonNull(entityIds, "entityIds must not be null")));
    }

    /**
     * 使用有序标识创建路径。
     *
     * @param entityIds 从外到内的实体标识
     */
    public EntityIdPath(List<? extends EntityId> entityIds) {
        if (Objects.isNull(entityIds) || entityIds.isEmpty()) {
            throw new IllegalArgumentException("Entity identifier path must not be empty");
        }
        this.entityIds = List.copyOf(entityIds);
        if (this.entityIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Entity identifier path must not contain null");
        }
    }

    /**
     * 返回独立迭代器，迭代器删除不会影响当前路径。
     *
     * @return 标识迭代器
     */
    public Iterator<EntityId> iterator() {
        return new ArrayList<>(entityIds).iterator();
    }

    /**
     * 返回路径第一个标识。
     *
     * @param <T> 标识类型
     * @return 聚合根标识
     */
    @SuppressWarnings("unchecked")
    public <T extends EntityId> T first() {
        return (T) entityIds.get(0);
    }

    /**
     * 返回路径最后一个标识。
     *
     * @param <T> 标识类型
     * @return 事件源标识
     */
    @SuppressWarnings("unchecked")
    public <T extends EntityId> T last() {
        return (T) entityIds.get(entityIds.size() - 1);
    }

    /**
     * 返回去掉第一个标识后的路径；单元素路径返回 {@code null}。
     *
     * @return 剩余路径或 {@code null}
     */
    public EntityIdPath rest() {
        return subPath(1, entityIds.size());
    }

    /**
     * 返回去掉最后一个标识后的路径；单元素路径返回 {@code null}。
     *
     * @return 父路径或 {@code null}
     */
    public EntityIdPath parent() {
        return subPath(0, entityIds.size() - 1);
    }

    /**
     * 返回路径长度。
     *
     * @return 标识数量
     */
    public int size() {
        return entityIds.size();
    }

    /**
     * 返回稳定的类型化路径文本。
     *
     * @return 类型化路径文本
     */
    @JsonValue
    public String asString() {
        return entityIds.stream().map(EntityId::asTypedString).collect(java.util.stream.Collectors.joining(PATH_SEPARATOR));
    }

    private EntityIdPath subPath(int fromIndex, int toIndex) {
        if (toIndex - fromIndex <= 0) {
            return null;
        }
        return new EntityIdPath(entityIds.subList(fromIndex, toIndex));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EntityIdPath that)) {
            return false;
        }
        return Objects.equals(entityIds, that.entityIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityIds);
    }

    @Override
    public String toString() {
        return asString();
    }

}
