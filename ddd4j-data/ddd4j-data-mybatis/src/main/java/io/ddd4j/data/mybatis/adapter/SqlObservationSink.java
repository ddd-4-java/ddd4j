package io.ddd4j.data.mybatis.adapter;

/**
 * SQL 执行观测消费端 SPI。
 */
@FunctionalInterface
public interface SqlObservationSink {

    /**
     * \u63a5\u6536\u4e00\u6b21 SQL \u6267\u884c\u7684\u89c2\u6d4b\u6570\u636e\u3002
     * @param observation SQL \u89c2\u6d4b\u6570\u636e
     */
    void accept(SqlObservation observation);
}
