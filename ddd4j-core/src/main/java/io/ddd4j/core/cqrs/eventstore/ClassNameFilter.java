package io.ddd4j.core.cqrs.eventstore;

/**
 * 类名白名单过滤器 SPI（回填自 3.0.x ee5d56a7）。
 *
 * <p>{@link EventDeserializer#isValidClassName} 仅做格式校验（防异常输入），
 * 本过滤器做业务白名单（防合法但恶意的类）。
 */
@FunctionalInterface
public interface ClassNameFilter {
    /**
     * 判断类名是否允许加载。
     *
     * @param className 类全限定名（已经过 {@link EventDeserializer#isValidClassName} 校验为合法格式）
     * @return 允许时 {@code true}；不允许时 {@code false}
     */
    boolean allows(String className);
}
