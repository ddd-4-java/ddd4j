package io.ddd4j.auth.datascope;

import java.util.Objects;

/**
 * 数据权限提供者
 *
 * @author wandl
 */
public interface DataScopeProvider {

    /**
     * 获取数据权限
     *
     * @param dataType 数据类型
     * @param data     数据，被注解标注的数据
     * @return 是否有数据权限
     */
    default boolean hasPermissions(String dataType, Object data) {
        return Objects.nonNull(data);
    }

}
