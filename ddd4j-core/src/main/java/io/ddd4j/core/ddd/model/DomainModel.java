package io.ddd4j.core.ddd.model;

import java.io.Serializable;

/** 领域模型的持久化无关标记与身份契约。 */
public interface DomainModel<ID extends Serializable> extends Serializable {
    ID id();
}
