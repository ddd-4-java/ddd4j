package io.ddd4j.core.api.contract.enums;

import java.io.Serializable;

/**
 * 双值枚举接口
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
public interface IEnum<T extends Serializable> {
    T getCode();

    String getDesc();
}
