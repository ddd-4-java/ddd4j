package io.ddd4j.data.jpa;

/**
 * JPA 持久化对象使用的纯 Java 雪花 ID 门面。
 *
 * <p>保留原有类名以兼容调用方，但不再实现 Hibernate
 * {@code IdentifierGenerator}，从而保持通用 JPA 模块的 provider-neutral。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class SnowflakeIdGenerator {

    private static final SnowflakeIdStrategy STRATEGY = new SnowflakeIdStrategy();

    private SnowflakeIdGenerator() {
    }

    public static long nextId() {
        return STRATEGY.generate();
    }
}
