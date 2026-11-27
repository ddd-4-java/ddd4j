package io.ddd4j.core.ddd.event;

import com.fasterxml.jackson.annotation.JsonValue;

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

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 路径分隔符。
     */
    public static final String PATH_SEPARATOR = "/";

    /** \u4ece\u805a\u5408\u6839\u5230\u5b9e\u4f53\u7684\u6807\u8bc6\u8def\u5f84\u3002 */
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
     * 返回稳定的类型化路径文本。
     *
     * @return 类型化路径文本
     */
    @JsonValue
    public String asString() {
        return entityIds.stream().map(EntityId::asTypedString).collect(java.util.stream.Collectors.joining(PATH_SEPARATOR));
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
