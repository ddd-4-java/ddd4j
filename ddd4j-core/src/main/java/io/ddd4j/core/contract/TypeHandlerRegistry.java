package io.ddd4j.core.contract;

/**
 * TypeHandler 注册 SPI（纯 Java，零框架依赖）。
 *
 * <p>各 ORM 框架适配层将自定义类型转换器注册到该注册表。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
public interface TypeHandlerRegistry {

    /**
     * 注册一个 TypeHandler
     */
    <T> void register(Class<T> javaType, TypeHandler<T, ?> handler);

    /**
     * 查询一个 TypeHandler
     */
    <T> TypeHandler<T, ?> lookup(Class<T> javaType);

    /**
     * TypeHandler 抽象
     */
    interface TypeHandler<J, S> {
        S serialize(J value);
        J deserialize(S stored);
    }
}
