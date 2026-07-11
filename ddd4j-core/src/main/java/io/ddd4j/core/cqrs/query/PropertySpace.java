package io.ddd4j.core.cqrs.query;

/**
 * 查询属性所属的模型空间。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public enum PropertySpace {

    /**
     * 领域模型属性，由 Repository 映射为持久化属性。
     */
    DOMAIN,

    /**
     * 持久化对象属性，仅允许在显式 PO 作用域中使用。
     */
    PERSISTENCE
}
