package io.ddd4j.data.mybatis.plugins.observation;

/**
 * MyBatis-Plus SQL 执行观测消费端 SPI。
 */
@FunctionalInterface
public interface SqlObservationSink {

    void accept(SqlObservation observation);
}
