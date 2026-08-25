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
package io.ddd4j.spring.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * Spring JPA 投影位置实体。
 *
 * <p>对应数据库表 {@code DDD4J_PROJECTION_POSITION}（与 Quarkus 运行时统一，可通过 {@code @Table} 改名）。
 * 持久化读侧视图的增量拉取偏移量，重启后从上次位置继续拉取。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Entity
@Table(name = "DDD4J_PROJECTION_POSITION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpringJpaProjectionPosition implements ProjectionPosition, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件流 ID
     */
    @Id
    @Column(name = "STREAM_ID", nullable = false, length = 250, updatable = false)
    private String streamId;

    /**
     * 下一条待处理的事件序号
     */
    @Column(name = "NEXT_EVENT_NUMBER", nullable = false, updatable = true)
    private long nextEventNumber;

    @Override
    public long getNextEventNumber() {
        return nextEventNumber;
    }

    /**
     * 推进到下一个位置，返回新的不可变实例。
     * <p>
     * 遵循 {@link ProjectionPosition} 接口契约：不修改当前实例，返回包含新偏移量的新对象。
     * 调用方通过 {@code repository.save(新实例)} 持久化（JPA merge 语义）。
     *
     * @param nextEventNumber 新的下一个事件号
     * @return 包含新偏移量的新实例
     */
    @Override
    public ProjectionPosition withNextEventNumber(long nextEventNumber) {
        return new SpringJpaProjectionPosition(this.streamId, nextEventNumber);
    }
}
