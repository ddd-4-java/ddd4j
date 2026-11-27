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

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 1L;

    /** \u6295\u5f71\u6d41\u6807\u8bc6\u3002 */
    private final String streamId;

    /** \u4e0b\u4e00\u4e2a\u5f85\u5904\u7406\u4e8b\u4ef6\u53f7\u3002 */
    private final long nextEventNumber;

    /**
     * \u521b\u5efa\u6295\u5f71\u4f4d\u7f6e\uff0c\u5e76\u6821\u9a8c\u6d41\u6807\u8bc6\u4e0e\u4e8b\u4ef6\u53f7\u3002
     * @param streamId \u6295\u5f71\u6d41\u6807\u8bc6
     * @param nextEventNumber \u4e0b\u4e00\u4e2a\u5f85\u5904\u7406\u4e8b\u4ef6\u53f7\uff0c\u4e0d\u53ef\u4e3a\u8d1f\u6570
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
     * zero() 方法。
     * @param streamId 字符串参数。
     * @return 当前对象自身，支持链式调用。
     */
    public static DefaultProjectionPosition zero(String streamId) {
        return new DefaultProjectionPosition(streamId, 0);
    }

    /**
     * withNextEventNumber() 方法。
     * @param nextEventNumber nextEventNumber 参数。
     * @return 方法执行结果。
     */
    @Override
    public ProjectionPosition withNextEventNumber(long nextEventNumber) {
        if (nextEventNumber == this.nextEventNumber) {
            return this;
        }
        return new DefaultProjectionPosition(streamId, nextEventNumber);
    }

    /**
     * 判断布尔状态。
     * @param position position 参数。
     * @return 判断结果。
     */
    public boolean isSameStream(ProjectionPosition position) {
        return Objects.nonNull(position) && Objects.equals(streamId, position.getStreamId());
    }
}
