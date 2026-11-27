package io.ddd4j.core.api;

import io.ddd4j.core.exception.BizRuntimeException;

import java.io.Serializable;
import java.util.Objects;

/**
 * 统一接口响应，标准的响应数据结构
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface IR extends Serializable {
    /**
     * \u83b7\u53d6\u54cd\u5e94\u7801
     * @return \u54cd\u5e94\u7801
     */
    Serializable getCode();

    /**
     * \u83b7\u53d6\u54cd\u5e94\u6d88\u606f
     * @return \u54cd\u5e94\u6d88\u606f
     */
    String getMsg();

    /**
     * \u83b7\u53d6\u54cd\u5e94\u6570\u636e
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @return \u54cd\u5e94\u6570\u636e
     */
    <T> T getData();

    /**
     * \u662f\u5426\u54cd\u5e94\u6210\u529f
     * @return \u5e03\u5c14\u503c\uff0c\u8868\u793a\u54cd\u5e94\u662f\u5426\u6210\u529f
     */
    Boolean isOk();

    /**
     * \u662f\u5426\u54cd\u5e94\u6210\u529f
     * @param notOkThrows \u54cd\u5e94\u5931\u8d25\u629b\u51fa\u5f02\u5e38\u6d88\u606f
     */
    default void isOk(String notOkThrows) {
        if (!isOk()) {
            throw new BizRuntimeException(notOkThrows + " -> {}", this);
        }
    }

    /**
     * \u83b7\u53d6\u54cd\u5e94\u6570\u636e
     * @param notOkThrows \u54cd\u5e94\u5931\u8d25\u629b\u51fa\u5f02\u5e38\u6d88\u606f
     * @param <T> \u54cd\u5e94\u6570\u636e\u7c7b\u578b
     * @return \u54cd\u5e94\u6570\u636e
     */
    default <T> T getData(String notOkThrows) {
        if (!isOk()) {
            throw new BizRuntimeException(notOkThrows + " -> {}", this);
        }
        T data = getData();
        if (Objects.isNull(data)) {
            throw new BizRuntimeException(notOkThrows + " -> {}", this);
        }
        return data;
    }
}