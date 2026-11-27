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
package io.ddd4j.data.projection.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 投影位置 JPA 实体：落地 {@code io.ddd4j.core.cqrs.readmodel.ProjectionPosition}
 * 的持久化形态（core 的 {@code DefaultProjectionPosition} 为不可变值对象，不入库）。
 *
 * <p>以视图的流标识作为主键，保存下一条待处理事件的编号。
 * {@link EntityManagerProjectionPositionRepository} 在调用方的事务中更新检查点。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see EntityManagerProjectionPositionRepository
 * @since 2.0.x
 */
@Entity
@Table(name = "ddd4j_projection_position")
public class ProjectionPositionEntity {

    /**
     * 投影流 ID（视图唯一标识，自然主键）。
     */
    @Id
    @Column(name = "stream_id", nullable = false, length = 250, updatable = false)
    private String streamId;

    /**
     * 下一个待处理事件号（0-based 位置计数，non-versionable）。
     */
    @Column(name = "next_event_number", nullable = false)
    private long nextEventNumber;

    /** \u521b\u5efa\u4f9b JPA \u52a0\u8f7d\u7684\u7a7a\u5b9e\u4f53\u3002 */
    public ProjectionPositionEntity() {
    }

    /**
     * \u521b\u5efa\u6295\u5f71\u6d88\u8d39\u4f4d\u7f6e\u5b9e\u4f53\u3002
     * @param streamId \u6295\u5f71\u6d41\u6807\u8bc6
     * @param nextEventNumber \u4e0b\u4e00\u4e2a\u5f85\u5904\u7406\u4e8b\u4ef6\u53f7
     */
    public ProjectionPositionEntity(String streamId, long nextEventNumber) {
        this.streamId = streamId;
        this.nextEventNumber = nextEventNumber;
    }

    /**
     * 构造归零实体（对应 core {@code DefaultProjectionPosition#zero}）。
     *
     * @param streamId 投影流 ID
     * @return 位置为 0 的实体
     */
    public static ProjectionPositionEntity zero(String streamId) {
        return new ProjectionPositionEntity(streamId, 0L);
    }

    /** @return \u6295\u5f71\u6d41\u6807\u8bc6 */
    public String getStreamId() {
        return streamId;
    }

    /** @param streamId \u6295\u5f71\u6d41\u6807\u8bc6 */
    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    /** @return \u4e0b\u4e00\u4e2a\u5f85\u5904\u7406\u4e8b\u4ef6\u53f7 */
    public long getNextEventNumber() {
        return nextEventNumber;
    }

    /** @param nextEventNumber \u4e0b\u4e00\u4e2a\u5f85\u5904\u7406\u4e8b\u4ef6\u53f7 */
    public void setNextEventNumber(long nextEventNumber) {
        this.nextEventNumber = nextEventNumber;
    }
}
