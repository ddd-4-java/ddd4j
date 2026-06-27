package io.ddd4j.data.mybatis.typehandlers;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import io.ddd4j.kit.lang.JsonKit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.ibatis.type.BaseTypeHandler;
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
 * string->转list集合
 *
 * 一般常用于json转集合对象，只能转list，
 * 比如我们数据库中有个List的json,如"[{'id':1,'name':'zhouhengzhe'},{'id':2,'name':'zhouhengzhe'}]",此时他转换过来的对象为
 * public class User{
 *     private Integer id;
 *     private String name;
 * }
 *
 *
 * 那么我们在mybatisplus中使用方式就为，
 * 首先定义一个继承自ListTypeHandler的类，比如叫UserListTypeHandler
 * public class UserListTypeHandler extends ListTypeHandler<User> {
 *     @Override
 *     protected TypeReference<List<User>> elementType() {
 *         return new TypeReference<List<User>>() {};
 *     }
 * }
 *
 *
 * 然后在mybatis中对应的实体类上加上注解
 * 整个对象上要加@TableName("user_list",autoResultMap = true)
 *
 *
 * 其属性上为
 * @TableField(typeHandler = UserListTypeHandler.class)
 * private List<User> users;
 *
 *
 * @author zhouhengzhe
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
     * 具体类型，由子类提供
     *
     * @return 具体类型
     */
    protected abstract TypeReference<List<T>> elementType();
}