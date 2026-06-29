/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.data.mybatis.dto;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 带排序的分页 DTO 基类。
 *
 * <p>从 {@code ddd4j-core} 迁入（3.4.x 起），消除核心模块对 MyBatis-Plus 的耦合。
 */
public abstract class AbstractOrderedPaginationDTO extends AbstractPaginationDTO {

    @Schema(description = "排序信息")
    private List<OrderItem> orders;

    public List<OrderItem> getOrders() {
        return orders;
    }

    public void setOrders(List<OrderItem> orders) {
        this.orders = orders;
    }
}
