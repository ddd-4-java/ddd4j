/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.boot.core.entity;

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
public class PaginationEntity<T extends Model<?>> extends BaseEntity<T> {

    protected static final int DEFAULT_LIMIT = 15;

    /**
     * 分页起始位置
     */
    @TableField(exist = false)
    private int offset = 0;
    /**
     * 每页记录数
     */
    @TableField(exist = false)
    private int limit = 15;
    /**
     * 当前页码
     */
    @TableField(exist = false)
    private int pageNo;
    /**
     * 总页数
     */
    @TableField(exist = false)
    private int totalPage;
    /**
     * 总记录数
     */
    @TableField(exist = false)
    private int totalCount;

    /**
     * 排序信息
     */
    @TableField(exist = false)
    private List<OrderItem> orders;

    public int getPageNo() {
        return pageNo < 0 ? (getOffset() / getLimit() + 1) : pageNo;
    }

    public int getOffset() {
        // 计算第一条记录的位置，Oracle分页是通过rownum进行的，而rownum是从1开始的
        return offset < 0 ? (pageNo < 0 ? 0 : ((getPageNo() - 1) * getLimit() + 1)) : offset;
    }

    public int getLimit() {
        return limit <= 0 ? DEFAULT_LIMIT : limit;
    }

}
