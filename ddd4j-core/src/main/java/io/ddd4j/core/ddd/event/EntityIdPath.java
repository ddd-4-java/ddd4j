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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/**
 * 从聚合根到事件源实体的有序标识路径。
 */
public final class EntityIdPath implements Serializable {
    /**
     * 路径分隔符。
     */
    public static final String PATH_SEPARATOR = "/";
    /**
     * Java 序列化版本标识。
     */
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 段内类型与值的分隔符（与 {@link EntityId#asTypedString()} 的拼接约定一致）。
     */
    private static final String TYPE_SEPARATOR = ":";

    /**
     * 从聚合根到实体的标识路径。
     */
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
        if (entityIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Entity identifier path must not contain null");
        }
        this.entityIds = List.copyOf(entityIds);
    }

    /**
     * 判断路径文本是否为合法的 {@link #asString()} 形态（不抛异常的布尔校验位）。
     *
     * <p>与 {@link #valueOf(String)} 共用同一解析逻辑，判定必须一致：
     * {@code isValid(path) == true} 当且仅当 {@code valueOf(path)} 可构造成功。
     * 对 {@code null}、空白、空段、段内缺 {@code :} 或 type/value 任一为空等
     * 非法输入返回 {@code false}（承接 {@code valueOf} 抛出的
     * {@link IllegalArgumentException}），对任意输入不抛出异常——
     * 显式布尔判定取代 fuin 先例的静默 {@code null} 语义。
     *
     * @param path {@code Type:value} 依次以 {@code /} 连接的路径文本，可为 {@code null}
     * @return 合法返回 {@code true}，其余返回 {@code false}
     */
    public static boolean isValid(String path) {
        try {
            valueOf(path);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
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
        int segmentStart = 0;
        // 跳过转义字符，使成对的反斜杠之后仍可使用斜杠分段。
        for (int index = 0; index < path.length(); index++) {
            char current = path.charAt(index);
            if (current == '\\') {
                index++;
            } else if (current == '/') {
                parsed.add(parseSegment(unescape(path.substring(segmentStart, index)), path));
                segmentStart = index + 1;
            }
        }
        parsed.add(parseSegment(unescape(path.substring(segmentStart)), path));
        return new EntityIdPath(parsed);
    }

    /**
     * 解析单个带类型的实体标识路径段。
     *
     * @param segment 从路径中取得的实体标识段
     * @param path    用于诊断的原始路径
     * @return 通过标识工厂恢复的实体标识
     * @throws IllegalArgumentException 路径段为空或类型和标识分隔格式无效
     */
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
        if (Objects.isNull(id)) {
            // 未注册类型回退为 StringEntityId（与历史行为一致）
            id = new StringEntityId(value);
        }
        return id;
    }

    /**
     * 转义段分隔符与反斜杠。
     *
     * <p>仅在字符串包含 {@code /} 或 {@code \} 时才插入转义符；简单值
     * （如 {@code "String:order-1"} 或 {@code "OrderId:o-1"}）保持原样输出。
     *
     * @param value 待编码的标识字符串
     * @return 可作为路径段使用的编码标识
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
     *
     * @param value 待解码的路径段
     * @return 解码后的原始标识
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

    /**
     * 写出 Java 8 可读取的实体标识路径，包括从历史对象流读回的路径。
     *
     * @param output 对象输出流
     * @throws IOException 写入序列化字段失败时
     * @serialData 原 {@code entityIds} 字段按独立的不可修改列表快照写出。
     */
    @Serial
    private void writeObject(ObjectOutputStream output) throws IOException {
        ObjectOutputStream.PutField fields = output.putFields();
        fields.put("entityIds", Collections.unmodifiableList(new ArrayList<>(entityIds)));
        output.writeFields();
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
     * 按索引区间截取连续实体标识。
     *
     * @param fromIndex 起始索引，包含该位置
     * @param toIndex   结束索引，不含该位置
     * @return 标识子路径，区间为空时返回空
     */
    private EntityIdPath subPath(int fromIndex, int toIndex) {
        if (toIndex - fromIndex <= 0) {
            return null;
        }
        return new EntityIdPath(entityIds.subList(fromIndex, toIndex));
    }

    /**
     * 根据<code>entityIds</code>比较对象的值是否相等。
     *
     * @param object 用于此操作的 <code>object</code>（<code>Object</code>）
     * @return 对象满足此实现的相等条件时为真
     */
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

    /**
     * 用<code>entityIds</code>计算哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(entityIds);
    }

    /**
     * 生成用于调试和日志的对象描述。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return asString();
    }

}
