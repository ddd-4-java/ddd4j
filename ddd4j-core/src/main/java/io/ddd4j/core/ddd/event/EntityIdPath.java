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
     * 段内类型与值的分隔符（与 {@link StringEntityId#asTypedString()} 的拼接约定一致）。
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
     * 解析 {@link #asString()} 文本重建路径（与序列化对偶，Jackson 反序列化 + 事件回放使用）。
     *
     * <p>解析契约：按 {@link #PATH_SEPARATOR} 分段，每段按<b>首个</b> {@code :} 切成
     * type 文本与 value 文本，重建为字符串标识。空串／空白／存在空段／段内缺 {@code :}
     * 或 type、value 任一为空时抛出 {@link IllegalArgumentException}（消息含出错段原文）。
     *
     * <p><b>限制</b>：
     * <ul>
     *   <li>回读段一律重建为 {@link StringEntityId}（保留 value；重序列化后 type 统一为
     *       {@code String:}），自定义 EntityId 实现类不还原为其原始类——类型注册表留待后续 ADR</li>
     *   <li>值内含 {@code /} 或 {@code :} 的标识不受支持（typed-string 惯例约束）</li>
     * </ul>
     *
     * @param path {@code Type:value} 依次以 {@code /} 连接的路径文本
     * @return 重建的实体标识路径
     * @throws IllegalArgumentException 路径为空或格式非法
     */
    @JsonCreator
    public static EntityIdPath valueOf(String path) {
        if (StrKit.isBlank(path)) {
            throw new IllegalArgumentException("Entity id path must not be blank: '" + path + "'");
        }
        List<EntityId> parsed = new ArrayList<>();
        for (String segment : path.split(PATH_SEPARATOR, -1)) {
            parsed.add(parseSegment(segment, path));
        }
        return new EntityIdPath(parsed);
    }

    private static EntityId parseSegment(String segment, String path) {
        if (StrKit.isBlank(segment)) {
            throw new IllegalArgumentException(
                    "Entity id path contains a blank segment: '" + segment + "' (path: '" + path + "')");
        }
        int separatorIndex = segment.indexOf(TYPE_SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex == segment.length() - 1) {
            throw new IllegalArgumentException(
                    "Entity id path segment must be in 'Type:value' form but was: '" + segment + "' (path: '" + path + "')");
        }
        return new StringEntityId(segment.substring(separatorIndex + 1));
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
     * @return 类型化路径文本
     */
    @JsonValue
    public String asString() {
        return entityIds.stream().map(EntityId::asTypedString).collect(java.util.stream.Collectors.joining(PATH_SEPARATOR));
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
