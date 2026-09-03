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
package io.ddd4j.core.ddd.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.ddd4j.kit.lang.StrKit;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * 从聚合根到事件源实体的有序标识路径。
 */
public final class EntityIdPath implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 路径分隔符。
     */
    public static final String PATH_SEPARATOR = "/";

    /**
     * 段内类型与值的分隔符（与 {@link EntityId#asTypedString()} 的拼接约定一致）。
     */
    private static final String TYPE_SEPARATOR = ":";

    private final List<EntityId> entityIds;

    /**
     * 使用有序标识创建路径。
     *
     * @param entityIds 从外到内的实体标识
     */
    public EntityIdPath(EntityId... entityIds) {
        this(Arrays.asList(Objects.requireNonNull(entityIds, "entityIds must not be null")));
    }

    /**
     * 使用有序标识创建路径。
     *
     * @param entityIds 从外到内的实体标识
     */
    public EntityIdPath(List<? extends EntityId> entityIds) {
        if (Objects.isNull(entityIds) || entityIds.isEmpty()) {
            throw new IllegalArgumentException("Entity identifier path must not be empty");
        }
        this.entityIds = List.copyOf(entityIds);
        if (this.entityIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Entity identifier path must not contain null");
        }
    }

    /**
     * 返回独立迭代器，迭代器删除不会影响当前路径。
     *
     * @return 标识迭代器
     */
    public Iterator<EntityId> iterator() {
        return new ArrayList<>(entityIds).iterator();
    }

    /**
     * 返回路径第一个标识。
     *
     * @param <T> 标识类型
     * @return 聚合根标识
     */
    @SuppressWarnings("unchecked")
    public <T extends EntityId> T first() {
        return (T) entityIds.get(0);
    }

    /**
     * 返回路径最后一个标识。
     *
     * @param <T> 标识类型
     * @return 事件源标识
     */
    @SuppressWarnings("unchecked")
    public <T extends EntityId> T last() {
        return (T) entityIds.get(entityIds.size() - 1);
    }

    /**
     * 返回去掉第一个标识后的路径；单元素路径返回 {@code null}。
     *
     * @return 剩余路径或 {@code null}
     */
    public EntityIdPath rest() {
        return subPath(1, entityIds.size());
    }

    /**
     * 返回去掉最后一个标识后的路径；单元素路径返回 {@code null}。
     *
     * @return 父路径或 {@code null}
     */
    public EntityIdPath parent() {
        return subPath(0, entityIds.size() - 1);
    }

    /**
     * 返回路径长度。
     *
     * @return 标识数量
     */
    public int size() {
        return entityIds.size();
    }

    /**
     * 解析 {@link #asString()} 文本重建路径（与序列化对偶，Jackson 反序列化 + 事件回放使用）。
     *
     * <p>解析契约：
     * <ul>
     *   <li>按 {@link #PATH_SEPARATOR} 分段，保留尾部空段</li>
     *   <li>每段独立做 {@link #unescape(String)}，再按<b>首个</b> {@link #TYPE_SEPARATOR}
     *       切成 type 文本与 value 文本；value 中的后续 {@code :} 全部归属 value</li>
     *   <li>按 type 通过 {@link EntityIdRegistry} 还原自定义 {@link EntityId}；
     *       未注册类型回退为 {@link StringEntityId}（与历史行为一致）</li>
     *   <li>空串／空白／存在空段／段内缺 {@code :} 或 type、value 任一为空时
     *       抛出 {@link IllegalArgumentException}（消息含出错段原文）</li>
     * </ul>
     *
     * <p>值内含 {@code /} 或 {@code \} 的标识需在序列化时转义（{@link #escape(String)}）。
     * 值内含 {@code :} <b>无需</b>转义——通过「首个 {@code :} 切分」约定天然支持。
     *
     * @param path {@code Type:value} 依次以 {@code /} 连接的路径文本
     * @return 重建的实体标识路径
     * @throws IllegalArgumentException 路径为空或格式非法
     */
    @JsonCreator
    public static EntityIdPath valueOf(String path) {
        if (!StrKit.hasText(path)) {
            throw new IllegalArgumentException("Entity id path must not be blank: '" + path + "'");
        }
        List<EntityId> parsed = new ArrayList<>();
        // 关键：使用负向后瞻 (?<!\\) 匹配未被反斜杠转义的 '/'，避免值内被转义的
        // '\/' 被误切为段分隔符。整体 unescape 后再切分会破坏段结构。
        for (String rawSegment : path.split("(?<!\\\\)" + PATH_SEPARATOR, -1)) {
            String segment = unescape(rawSegment);
            parsed.add(parseSegment(segment, path));
        }
        return new EntityIdPath(parsed);
    }

    private static EntityId parseSegment(String segment, String path) {
        if (!StrKit.hasText(segment)) {
            throw new IllegalArgumentException(
                    "Entity id path contains a blank segment: '" + segment + "' (path: '" + path + "')");
        }
        int separatorIndex = segment.indexOf(TYPE_SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex == segment.length() - 1) {
            throw new IllegalArgumentException(
                    "Entity id path segment must be in 'Type:value' form but was: '"
                            + segment + "' (path: '" + path + "')");
        }
        String typeName = segment.substring(0, separatorIndex);
        String value = segment.substring(separatorIndex + 1);
        EntityId id = EntityIdRegistry.valueOf(typeName, value);
        if (id == null) {
            // 未注册类型回退为 StringEntityId（与历史行为一致）
            id = new StringEntityId(value);
        }
        return id;
    }

    /**
     * 返回稳定的类型化路径文本。
     *
     * <p>序列化时对每段做 {@link #escape(String)}，仅转义两个有歧义的字符：
     * <ul>
     *   <li>{@code /} → {@code \/} —— 段分隔符，必须转义以避免值内 {@code /} 与段分隔冲突</li>
     *   <li>{@code \} → {@code \\} —— 转义字符本身</li>
     * </ul>
     *
     * <p>段内的 {@code :} <b>不</b>转义（保持简单路径的视觉简洁），反序列化端通过
     * 「首个 {@code :} 切分 type/value」约定，剩余的 {@code :} 全部归属 value。
     *
     * @return 类型化路径文本
     */
    @JsonValue
    public String asString() {
        return entityIds.stream()
                .map(EntityId::asTypedString)
                .map(EntityIdPath::escape)
                .collect(java.util.stream.Collectors.joining(PATH_SEPARATOR));
    }

    /**
     * 转义段分隔符与反斜杠。
     *
     * <p>仅在字符串包含 {@code /} 或 {@code \} 时才插入转义符；简单值
     * （如 {@code "String:order-1"} 或 {@code "OrderId:o-1"}）保持原样输出。
     */
    private static String escape(String value) {
        if (value.indexOf('\\') < 0 && value.indexOf('/') < 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length() + 4);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '/') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 反转义段分隔符（{@link #escape(String)} 的对偶）。
     */
    private static String unescape(String value) {
        if (value.indexOf('\\') < 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                if (next == '\\' || next == '/') {
                    sb.append(next);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private EntityIdPath subPath(int fromIndex, int toIndex) {
        if (toIndex - fromIndex <= 0) {
            return null;
        }
        return new EntityIdPath(entityIds.subList(fromIndex, toIndex));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EntityIdPath that)) {
            return false;
        }
        return Objects.equals(entityIds, that.entityIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityIds);
    }

    @Override
    public String toString() {
        return asString();
    }

}
