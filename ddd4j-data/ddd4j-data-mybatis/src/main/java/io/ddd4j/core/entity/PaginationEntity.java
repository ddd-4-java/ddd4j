/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Deprecated(since = "3.4.x", forRemoval = true)
@SuppressWarnings("removal")
public class PaginationEntity<T extends Model<?>> extends BaseEntity<T> {

    protected static final int DEFAULT_LIMIT = 15;

    @TableField(exist = false)
    private int offset = 0;

    @TableField(exist = false)
    private int limit = 15;

    @TableField(exist = false)
    private int pageNo;

    @TableField(exist = false)
    private int totalPage;

    @TableField(exist = false)
    private int totalCount;

    @TableField(exist = false)
    private List<OrderItem> orders;

    public int getPageNo() {
        return pageNo < 0 ? (getOffset() / getLimit() + 1) : pageNo;
    }

    public int getOffset() {
        return offset < 0 ? (pageNo < 0 ? 0 : ((getPageNo() - 1) * getLimit() + 1)) : offset;
    }

    public int getLimit() {
        return limit <= 0 ? DEFAULT_LIMIT : limit;
    }

    public List<OrderItem> getOrders() {
        return orders;
    }

}
