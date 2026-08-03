package io.ddd4j.core.ddd.event;

import java.io.Serializable;

/**
 * 实体类型的稳定标识。
 */
public interface EntityType extends Serializable {

    /**
     * 返回可用于日志、序列化和类型化标识的文本。
     *
     * @return 实体类型文本
     */
    String asString();

}
