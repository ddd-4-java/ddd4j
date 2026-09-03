package io.ddd4j.core.contract.enums;

import java.io.Serializable;

/**
 * 双值枚举接口
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
/**
 * @deprecated 1.0.x 契约归位：与 3.0.x 对齐后，本类语义已由 {@code io.ddd4j.core.enums.IEnum} 承接。
 * 本类仅为保持 1.0.x 现有消费方兼容而保留，新代码请使用新包路径。
 */
@Deprecated
public interface IEnum<T extends Serializable> {
    T getCode();

    String getDesc();
}
