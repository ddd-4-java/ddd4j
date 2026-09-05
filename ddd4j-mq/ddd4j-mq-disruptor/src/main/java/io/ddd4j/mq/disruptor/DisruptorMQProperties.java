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
package io.ddd4j.mq.disruptor;

import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.disruptor.util.WaitStrategys;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * LMAX Disruptor 本地 MQ 配置（前缀 {@code ddd4j.mq.disruptor}）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DisruptorMQProperties extends MQProperties {

    /**
     * RingBuffer 大小（须为 2 的幂）。
     */
    private int bufferSize = 1024;

    /**
     * 等待策略（枚举，与 {@link WaitStrategys} 对齐）。
     * Spring Boot 配置 yml 时按枚举名匹配：{@code blocking} / {@code yielding} / {@code busyspin} / {@code sleeping}。
     */
    private WaitStrategys waitStrategy = WaitStrategys.yielding;

    /**
     * 消费者线程名前缀
     */
    private String threadNamePrefix = "ddd4j-disruptor-";
}
