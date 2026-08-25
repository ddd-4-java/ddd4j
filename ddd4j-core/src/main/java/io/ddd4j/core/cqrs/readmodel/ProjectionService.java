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

/**
 * 投影位置服务。
 *
 * <p>对齐 ProjectionService 语义，完全独立实现，
 * 保持 ddd4j-core 纯 Java 小内核可独立使用。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface ProjectionService {

    /**
     * 重置投影位置到事件流起点。
     *
     * @param streamId 投影流 ID
     */
    void resetProjectionPosition(String streamId);

    /**
     * 读取下一个待处理事件号。
     *
     * @param streamId 投影流 ID
     * @return 下一个待处理事件号，不存在时返回 0
     */
    long readProjectionPosition(String streamId);

    /**
     * 更新投影位置。
     *
     * @param streamId        投影流 ID
     * @param nextEventNumber 下一个待处理事件号
     * @return 持久化后的投影位置
     */
    ProjectionPosition updateProjectionPosition(String streamId, long nextEventNumber);
}
