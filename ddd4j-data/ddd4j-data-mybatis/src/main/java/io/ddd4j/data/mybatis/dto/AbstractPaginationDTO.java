/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.data.mybatis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

/**
 * 分页 DTO 基类。
 *
 * <p>从 {@code ddd4j-core} 迁入（3.4.x 起），消除核心模块对 jakarta.validation 的耦合。
 */
public abstract class AbstractPaginationDTO {

    @Schema(example = "15", description = "每页记录数")
    @Min(value = 2, message = "每页至少2条数据")
    private int limit = 15;

    @Schema(example = "1", description = "当前页码")
    @Min(value = 1, message = "最小页码不能小于1")
    private int pageNo = 1;

    @Schema(description = "开始时间")
    private String beginTime;

    @Schema(description = "结束时间")
    private String endTime;

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }
}
