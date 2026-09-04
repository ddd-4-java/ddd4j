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
package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;

import java.util.List;

/**
 * 乐观锁版本冲突异常（ADR-0005）。
 *
 * <p>{@link EventStore#append(String, AggregateRootId, List, long)} 校验
 * {@code expectedVersion} 与流实际版本不一致时抛出。四字段对齐 esc-api
 * {@code WrongExpectedVersionException} 的已验证语义，但为 RuntimeException 子类且
 * 不显式声明 throws（ADR-0004 错误模型）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class AggregateVersionConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String aggregateType;
    private final String aggregateId;
    private final long expectedVersion;
    private final long actualVersion;

    /**
     * 创建版本冲突异常。
     *
     * @param aggregateType   聚合类型
     * @param aggregateId     聚合 ID
     * @param expectedVersion 期望的当前版本号
     * @param actualVersion   流的实际版本号
     */
    public AggregateVersionConflictException(String aggregateType, String aggregateId,
                                             long expectedVersion, long actualVersion) {
        super(String.format("Aggregate %s#%s version conflict: expected=%d, actual=%d",
                aggregateType, aggregateId, expectedVersion, actualVersion));
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    /**
     * 返回聚合类型。
     *
     * @return 聚合类型
     */
    public String aggregateType() {
        return aggregateType;
    }

    /**
     * 返回聚合 ID。
     *
     * @return 聚合 ID
     */
    public String aggregateId() {
        return aggregateId;
    }

    /**
     * 返回期望的当前版本号。
     *
     * @return 期望版本号
     */
    public long expectedVersion() {
        return expectedVersion;
    }

    /**
     * 返回流的实际版本号。
     *
     * @return 实际版本号
     */
    public long actualVersion() {
        return actualVersion;
    }
}
