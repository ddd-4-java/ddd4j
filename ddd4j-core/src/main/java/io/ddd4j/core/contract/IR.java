package io.ddd4j.core.contract;

import io.ddd4j.core.contract.exception.ServiceException;

import java.io.Serializable;

/**
 * 统一接口响应，标准的响应数据结构
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
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