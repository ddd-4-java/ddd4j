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
package io.ddd4j.core.cqrs.readmodel;

import io.ddd4j.kit.lang.StrKit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

/**
 * 默认不可变投影位置。
 *
 * <p>用于纯 Java 场景、测试场景，以及框架适配层没有自己的持久化实体时的
 * 最小实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Getter
@ToString
@EqualsAndHashCode
public final class DefaultProjectionPosition implements ProjectionPosition {

    private static final long serialVersionUID = 1L;

    /**
     * 投影流标识。
     */
    private final String streamId;

    /**
     * 下一个待处理事件号。
     */
    private final long nextEventNumber;

    /**
     * 创建投影位置，并校验流标识与事件号。
     *
     * @param streamId 投影流标识
     * @param nextEventNumber 下一个待处理事件号，不可为负数
     */
    public DefaultProjectionPosition(String streamId, long nextEventNumber) {
        if (StrKit.isBlank(streamId)) {
            throw new IllegalArgumentException("streamId must not be blank");
        }
        if (nextEventNumber < 0) {
            throw new IllegalArgumentException("nextEventNumber must not be negative");
        }
        this.streamId = streamId;
        this.nextEventNumber = nextEventNumber;
    }

    /**
     * 创建从事件号 0 开始的投影位置。
     *
     * @param streamId 投影流标识
     * @return 初始投影位置
     */
    public static DefaultProjectionPosition zero(String streamId) {
        return new DefaultProjectionPosition(streamId, 0);
    }

    @Override
    public ProjectionPosition withNextEventNumber(long nextEventNumber) {
        if (nextEventNumber == this.nextEventNumber) {
            return this;
        }
        return new DefaultProjectionPosition(streamId, nextEventNumber);
    }

    /**
     * 判断另一个投影位置是否属于同一流。
     *
     * @param position 待比较位置
     * @return 流标识相同时为 true
     */
    public boolean isSameStream(ProjectionPosition position) {
        return Objects.nonNull(position) && Objects.equals(streamId, position.getStreamId());
    }
}
