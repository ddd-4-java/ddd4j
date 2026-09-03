package io.ddd4j.data.mybatis.adapter;

/**
 * SQL 执行观测消费端 SPI。
 */
@FunctionalInterface
public interface SqlObservationSink {

    void accept(SqlObservation observation);
}
