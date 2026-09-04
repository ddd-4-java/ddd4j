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
package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.constant.ProjectionConstants;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Quarkus 标准 JPA 投影位置实体。
 *
 * <p>对应数据库表 {@code DDD4J_PROJECTION_POSITION}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Entity
@Table(name = ProjectionConstants.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuarkusJpaProjectionPosition implements ProjectionPosition, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件流 ID
     */
    @Id
    @Column(name = ProjectionConstants.COLUMN_STREAM_ID, nullable = false, length = 250, updatable = false)
    private String streamId;

    /**
     * 下一条待处理的事件序号
     */
    @Column(name = ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER, nullable = false, updatable = true)
    private long nextEventNumber;

    @Override
    public long getNextEventNumber() {
        return nextEventNumber;
    }

    /**
     * 返回一个 {@code nextEventNumber} 已更新的新实例（不可变契约）。
     *
     * @param nextEventNumber 新的下一个事件号
     * @return 包含新事件号的新实例，原实例不受影响
     */
    @Override
    public ProjectionPosition withNextEventNumber(long nextEventNumber) {
        return new QuarkusJpaProjectionPosition(this.streamId, nextEventNumber);
    }
}
