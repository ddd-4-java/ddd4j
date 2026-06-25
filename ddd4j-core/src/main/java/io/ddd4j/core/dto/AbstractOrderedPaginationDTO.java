/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.dto;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public abstract class AbstractOrderedPaginationDTO extends AbstractPaginationDTO {

    /**
     * 排序信息
     */
    @Schema(description = "排序信息")
    private List<OrderItem> orders;

    public List<OrderItem> getOrders() {
        return orders;
    }

    public void setOrders(List<OrderItem> orders) {
        this.orders = orders;
    }

}