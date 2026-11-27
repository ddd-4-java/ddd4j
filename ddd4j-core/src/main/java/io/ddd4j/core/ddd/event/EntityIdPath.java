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

import java.util.Collections;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.ddd4j.kit.lang.StrKit;

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
    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 1L;

    /**
     * 路径分隔符。
     */
    public static final String PATH_SEPARATOR = "/";

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
        this.entityIds = Collections.unmodifiableList(new ArrayList<>(entityIds));
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
        // 关键：使用负向后瞻 (?<!\\) 匹配未被反斜杠转义的 '/'，避免值内被转义的
        // '\/' 被误切为段分隔符。整体 unescape 后再切分会破坏段结构。
        for (String rawSegment : path.split("(?<!\\\\)" + PATH_SEPARATOR, -1)) {
            String segment = unescape(rawSegment);
            parsed.add(parseSegment(segment, path));
        }
        return new EntityIdPath(parsed);
    }

    /**
     * \u89e3\u6790\u5355\u4e2a\u5e26\u7c7b\u578b\u7684\u5b9e\u4f53\u6807\u8bc6\u8def\u5f84\u6bb5\u3002
     * @param segment \u4ece\u8def\u5f84\u4e2d\u53d6\u5f97\u7684\u5b9e\u4f53\u6807\u8bc6\u6bb5
     * @param path \u7528\u4e8e\u8bca\u65ad\u7684\u539f\u59cb\u8def\u5f84
     * @return \u901a\u8fc7\u6807\u8bc6\u5de5\u5382\u6062\u590d\u7684\u5b9e\u4f53\u6807\u8bc6
     * @throws IllegalArgumentException \u8def\u5f84\u6bb5\u4e3a\u7a7a\u6216\u7c7b\u578b\u548c\u6807\u8bc6\u5206\u9694\u683c\u5f0f\u65e0\u6548
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
     * \u8f6c\u4e49\u6bb5\u5206\u9694\u7b26\u4e0e\u53cd\u659c\u6760\u3002
     *
     *  <p>\u4ec5\u5728\u5b57\u7b26\u4e32\u5305\u542b {@code /} \u6216 {@code \} \u65f6\u624d\u63d2\u5165\u8f6c\u4e49\u7b26\uff1b\u7b80\u5355\u503c
     *  \uff08\u5982 {@code "String:order-1"} \u6216 {@code "OrderId:o-1"}\uff09\u4fdd\u6301\u539f\u6837\u8f93\u51fa\u3002
     *
     * @param value \u5f85\u7f16\u7801\u7684\u6807\u8bc6\u5b57\u7b26\u4e32
     * @return \u53ef\u4f5c\u4e3a\u8def\u5f84\u6bb5\u4f7f\u7528\u7684\u7f16\u7801\u6807\u8bc6
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
     * \u53cd\u8f6c\u4e49\u6bb5\u5206\u9694\u7b26\uff08{@link #escape(String)} \u7684\u5bf9\u5076\uff09\u3002
     *
     * @param value \u5f85\u89e3\u7801\u7684\u8def\u5f84\u6bb5
     * @return \u89e3\u7801\u540e\u7684\u539f\u59cb\u6807\u8bc6
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
     * \u6309\u7d22\u5f15\u533a\u95f4\u622a\u53d6\u8fde\u7eed\u5b9e\u4f53\u6807\u8bc6\u3002
     * @param fromIndex \u8d77\u59cb\u7d22\u5f15\uff0c\u5305\u542b\u8be5\u4f4d\u7f6e
     * @param toIndex \u7ed3\u675f\u7d22\u5f15\uff0c\u4e0d\u542b\u8be5\u4f4d\u7f6e
     * @return \u6807\u8bc6\u5b50\u8def\u5f84\uff0c\u533a\u95f4\u4e3a\u7a7a\u65f6\u8fd4\u56de\u7a7a
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
        if (!(object instanceof EntityIdPath)) {
            return false;
        }
        EntityIdPath that = (EntityIdPath) object;
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
