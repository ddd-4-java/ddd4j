package io.ddd4j.data.mybatis.typehandler;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import io.ddd4j.kit.lang.JsonKit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * string -&gt; 转 {@code List} 集合。
 *
 * <p>一般用于 JSON 转集合对象，只能转 List。
 *
 * <p>使用方式：
 * <pre>
 * // 1. 定义继承类，提供元素类型
 * public class UserListTypeHandler extends ListTypeHandler&lt;User&gt; {
 *     &#64;Override
 *     protected TypeReference&lt;List&lt;User&gt;&gt; elementType() {
 *         return new TypeReference&lt;List&lt;User&gt;&gt;() {};
 *     }
 * }
 *
 * // 2. 在 PO 上标注
 * &#64;TableName(value = "user_list", autoResultMap = true)
 * public class UserPO {
 *     &#64;TableField(typeHandler = UserListTypeHandler.class)
 *     private List&lt;User&gt; users;
 * }
 * </pre>
 *
 * @param <T> List 元素类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@MappedJdbcTypes(JdbcType.VARBINARY)
@MappedTypes({List.class})
public abstract class ListTypeHandler<T> extends BaseTypeHandler<List<T>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<T> parameter, JdbcType jdbcType) throws SQLException {
        String content = CollUtil.isEmpty(parameter) ? null : JsonKit.toJson(parameter);
        ps.setString(i, content);
    }

    @Override
    public List<T> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return this.toList(rs.getString(columnName));
    }

    @Override
    public List<T> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return this.toList(rs.getString(columnIndex));
    }

    @Override
    public List<T> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return this.toList(cs.getString(columnIndex));
    }

    private List<T> toList(String content) {
        try {
            return StrUtil.isBlank(content) ? new ArrayList<>() : JsonKit.DEFAULT_OBJECT_MAPPER.readValue(content, this.elementType());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 具体元素类型，由子类提供。
     *
     * @return List 的 TypeReference
     */
    protected abstract TypeReference<List<T>> elementType();
}
