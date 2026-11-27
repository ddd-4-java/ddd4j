package io.ddd4j.data.datascope;

import java.util.Objects;

/**
 * Provides business-specific data permission decisions.
 */
@FunctionalInterface
public interface DataScopeProvider {

    /**
     * Default provider: preserves legacy behavior by accepting non-null values.
     *
     * @return 当前操作得到的 <code>DataScopeProvider</code> 结果
     */
    static DataScopeProvider nonNullAllowed() {
        return (dataType, data) -> Objects.nonNull(data);
    }

    /**
     * Returns whether the annotated data is permitted for the given data type.
     *
     * @param dataType data domain or resource type declared by the annotation
     * @param data     annotated value
     * @return true when the current context can access the data
     */
    boolean hasPermissions(String dataType, Object data);
}
