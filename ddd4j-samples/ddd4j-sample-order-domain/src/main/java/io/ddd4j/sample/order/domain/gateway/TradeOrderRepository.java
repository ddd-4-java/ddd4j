package io.ddd4j.sample.order.domain.gateway;

import io.ddd4j.sample.order.domain.gateway.TradeOrderRepository;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.order.domain.model.TradeOrder;

public interface TradeOrderRepository extends Repository<TradeOrder, Long> {
    TradeOrder findByOrderNo(String orderNo);
    TradeOrder lockByOrderNo(String orderNo);
}
