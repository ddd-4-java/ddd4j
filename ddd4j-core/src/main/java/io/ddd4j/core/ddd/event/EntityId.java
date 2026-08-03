package io.ddd4j.core.ddd.event;

import java.io.Serializable;

/**
 * 领域实体标识的最小契约。
 */
public interface EntityId extends Serializable {

    /**
     * 返回实体类型。
     *
     * @return 实体类型
     */
    EntityType getType();

    /**
     * 返回不包含类型前缀的标识值。
     *
     * @return 标识值
     */
    String asString();

    /**
     * 返回包含类型前缀的稳定标识值。
     *
     * @return 类型化标识值
     */
    String asTypedString();

}
