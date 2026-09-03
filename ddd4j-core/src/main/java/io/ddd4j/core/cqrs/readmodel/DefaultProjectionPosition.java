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

    private final String streamId;

    private final long nextEventNumber;

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

    public boolean isSameStream(ProjectionPosition position) {
        return Objects.nonNull(position) && Objects.equals(streamId, position.getStreamId());
    }
}
