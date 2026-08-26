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

import io.ddd4j.kit.lang.JsonKit;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 事件载荷反序列化器：校验 eventType 合法性后按 Class.forName 还原，
 * 失败回退为 Map。
 *
 * <p>从存储层读取的 {@code eventType} 是不可信输入（可能被恶意写入）。
 * 本类在 {@code Class.forName} 之前校验类名格式，防止加载任意类。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public final class EventDeserializer {

    /**
     * 合法的 Java 全限定类名：至少一个包段，每段以字母/下划线/$开头。
     */
    private static final Pattern VALID_CLASS_NAME =
            Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)+$");

    private EventDeserializer() {
    }

    /**
     * 按 eventType 反序列化 payload。
     *
     * <p>优先校验类名格式，非法类名直接回退为 Map。
     * 格式合法但类不存在（被删除/重命名）时同样回退为 Map。
     *
     * @param payload   JSON 文本
     * @param eventType 事件类全限定名（来自存储层，不可信）
     * @return 强类型事件对象或 Map（回退）
     */
    @SuppressWarnings("unchecked")
    public static Object deserialize(String payload, String eventType) {
        if (!VALID_CLASS_NAME.matcher(eventType).matches()) {
            return JsonKit.toMap(payload);
        }
        try {
            Class<?> eventClass = Class.forName(eventType);
            return JsonKit.toObject(payload, eventClass);
        } catch (ClassNotFoundException e) {
            return JsonKit.toMap(payload);
        }
    }
}
