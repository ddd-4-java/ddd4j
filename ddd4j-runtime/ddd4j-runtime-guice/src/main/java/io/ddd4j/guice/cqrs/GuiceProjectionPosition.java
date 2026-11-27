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
package io.ddd4j.guice.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * Guice 环境默认的内存投影位置对象。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GuiceProjectionPosition implements ProjectionPosition, Serializable {

    /**
     * Java 序列化版本标识。
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件流 ID
     */
    private String streamId;
    /**
     * 下一条待处理的事件序号
     */
    private long nextEventNumber;

    /**
     * 以新事件位置构造同一流的投影检查点。
     *
     * <p>此实现更新<code>this.nextEventNumber = nextEventNumber</code>。</p>
     *
     * @param nextEventNumber 新的下一个事件号
     * @return 实际返回<code>this</code>，供调用方取得当前值
     */
    @Override
    public ProjectionPosition withNextEventNumber(long nextEventNumber) {
        this.nextEventNumber = nextEventNumber;
        return this;
    }
}
