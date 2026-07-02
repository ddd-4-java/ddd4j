package io.ddd4j.core.contract.enums;

import java.io.Serializable;

/**
 * 双值枚举接口
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface IEnum<T extends Serializable> {
    T getCode();

    String getDesc();
}
