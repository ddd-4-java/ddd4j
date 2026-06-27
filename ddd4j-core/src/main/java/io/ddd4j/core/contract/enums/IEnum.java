package io.ddd4j.core.contract.enums;

import java.io.Serializable;

/**
 * 双值枚举接口
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 */
public interface IEnum<T extends Serializable> {
    T getCode();

    String getDesc();
}
