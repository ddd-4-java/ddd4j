package io.ddd4j.core.contract;

import io.ddd4j.core.contract.exception.ServiceException;

import java.io.Serializable;

/**
 * 统一接口响应，标准的响应数据结构
 *
 * @author Jensen
 * @公众号 架构师修行录
 */

/**
 * @deprecated 1.0.x 契约归位：与 3.0.x 对齐后，本类语义已由 {@code io.ddd4j.core.api.IR} 承接。
 * 本类仅为保持 1.0.x 现有消费方兼容而保留，新代码请使用新包路径。
 */
@Deprecated
public interface IR extends Serializable {
    Serializable getCode();

    String getMsg();

    <T> T getData();

    Boolean isOk();

    default void isOk(String notOkThrows) {
        if (!isOk()) {
            throw new ServiceException(notOkThrows + " -> {}", this);
        }
    }

    default <T> T getData(String notOkThrows) {
        if (!isOk()) {
            throw new ServiceException(notOkThrows + " -> {}", this);
        }
        T data = getData();
        if (data == null) {
            throw new ServiceException(notOkThrows + " -> {}", this);
        }
        return data;
    }
}
