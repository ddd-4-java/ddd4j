package io.ddd4j.data.mybatis.typehandler;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.ddd4j.core.domain.event.TypeHandlerRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link TypeHandlerRegistry} 的 MyBatis 实现注册表。
 *
 * <p>将 ddd4j-data-mybatis 的 17 个 MyBatis {@code TypeHandler} 注册到 core 的 {@code TypeHandlerRegistry} SPI。
 *
 * <p>注意：MyBatis 的 {@code org.apache.ibatis.type.TypeHandler} 接口
 * （{@code setParameter} / {@code getResult}）与 core 的 {@code TypeHandlerRegistry.TypeHandler<J,S>}
 * （{@code serialize} / {@code deserialize}）签名不兼容。
 * 本注册表通过内置 JSON 适配器桥接，将 Java 对象的 JSON 序列化逻辑适配为 core 的 serialize/deserialize 语义。
 *
 * <p>实际 MyBatis TypeHandler 的注册仍由 MyBatis-Plus 的 {@code @MappedTypes} / SqlSessionFactory 完成，
 * 本注册表仅提供 core SPI 的统一查询入口，供非 MyBatis 场景或框架适配层使用。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@SuppressWarnings("unchecked")
public class MybatisTypeHandlerRegistry implements TypeHandlerRegistry {

    private final Map<Class<?>, TypeHandler<?, ?>> handlers = new ConcurrentHashMap<>();

    public MybatisTypeHandlerRegistry() {
        // 注册内置 JSON 类型转换器（Java 类型 → JSON 字符串）
        handlers.put(List.class, new ListJsonTypeHandler());
        handlers.put(Set.class, new SetJsonTypeHandler());
        handlers.put(JSONObject.class, new JsonObjectJsonTypeHandler());
        handlers.put(JSONArray.class, new JsonArrayJsonTypeHandler());
    }

    @Override
    public <T> void register(Class<T> javaType, TypeHandler<T, ?> handler) {
        handlers.put(javaType, handler);
    }

    @Override
    public <T> TypeHandler<T, ?> lookup(Class<T> javaType) {
        return (TypeHandler<T, ?>) handlers.get(javaType);
    }

    // ====== 内置 JSON 适配器（实现 core TypeHandlerRegistry.TypeHandler） ======

    /**
     * List ↔ JSON 字符串。
     */
    private static class ListJsonTypeHandler implements TypeHandler<List, String> {
        @Override
        public String serialize(List value) {
            if (Objects.isNull(value)) {
                return null;
            }
            return JSONUtil.toJsonStr(value);
        }

        @Override
        public List deserialize(String stored) {
            if (Objects.isNull(stored) || !org.springframework.util.StringUtils.hasLength(stored)) {
                return null;
            }
            return JSONUtil.parseArray(stored).toList(Object.class);
        }
    }

    /**
     * Set ↔ JSON 字符串。
     */
    private static class SetJsonTypeHandler implements TypeHandler<Set, String> {
        @Override
        public String serialize(Set value) {
            if (Objects.isNull(value)) {
                return null;
            }
            return JSONUtil.toJsonStr(value);
        }

        @Override
        public Set deserialize(String stored) {
            if (Objects.isNull(stored) || !org.springframework.util.StringUtils.hasLength(stored)) {
                return null;
            }
            Set<Object> set = new HashSet<>();
            JSONArray array = JSONUtil.parseArray(stored);
            for (Object o : array) {
                set.add(o);
            }
            return set;
        }
    }

    /**
     * Hutool JSONObject ↔ JSON 字符串。
     */
    private static class JsonObjectJsonTypeHandler implements TypeHandler<JSONObject, String> {
        @Override
        public String serialize(JSONObject value) {
            if (Objects.isNull(value)) {
                return null;
            }
            return value.toString();
        }

        @Override
        public JSONObject deserialize(String stored) {
            if (Objects.isNull(stored) || !org.springframework.util.StringUtils.hasLength(stored)) {
                return null;
            }
            return JSONUtil.parseObj(stored);
        }
    }

    /**
     * Hutool JSONArray ↔ JSON 字符串。
     */
    private static class JsonArrayJsonTypeHandler implements TypeHandler<JSONArray, String> {
        @Override
        public String serialize(JSONArray value) {
            if (Objects.isNull(value)) {
                return null;
            }
            return value.toString();
        }

        @Override
        public JSONArray deserialize(String stored) {
            if (Objects.isNull(stored) || !org.springframework.util.StringUtils.hasLength(stored)) {
                return null;
            }
            return JSONUtil.parseArray(stored);
        }
    }

}
