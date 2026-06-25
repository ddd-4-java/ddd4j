/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.ddd4j.core.ApiCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Collections;
import java.util.List;


@Schema(name = "Result", description = "分页查询结果对象")
@Data
public class Result<T> {

    /**
     * 状态码
     */
    @Schema(name = "code", type = "integer", description = "状态码")
    private int code = ApiCode.SC_SUCCESS.getCode();
    /**
     * 当前页码
     */
    @Schema(name = "current", type = "integer", format = "int64", description = "当前页码")
    private long current;
    /**
     * 每页显示条数，默认 15
     */
    @Schema(name = "size", type = "integer", format = "int64", description = "每页显示条数，默认 15")
    private long size = 15;
    /**
     * 总页码
     */
    @Schema(name = "pages", type = "integer", format = "int64", description = "总页码")
    private long pages;
    /**
     * 总记录数
     */
    @Schema(name = "total", type = "integer", format = "int64", description = "总记录数")
    private long total;
    /**
     * 数据集：bootstrap-table要求服务器返回的json包含：total，rows；不想修改前端的默认配置
     */
    @Schema(name = "rows", description = "数据集")
    private List<T> rows = Collections.emptyList();

    public Result() {
    }

    public Result(List<T> rows) {

        this.total = rows.size();
        this.current = 1;
        this.size = rows.size();
        this.pages = 1;
        this.rows = rows;

    }

    @SuppressWarnings("rawtypes")
    public Result(Page pageResult, List<T> rows) {

        this.total = pageResult.getTotal();
        this.current = pageResult.getCurrent();
        this.size = pageResult.getSize();
        this.pages = pageResult.getPages();
        this.rows = rows;

    }

}
