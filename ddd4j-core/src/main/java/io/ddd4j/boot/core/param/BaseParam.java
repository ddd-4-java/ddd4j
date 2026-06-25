/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.boot.core.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public abstract class BaseParam {

    /**
     * 请求发生的时间
     */
    @Schema(description = "请求发生的时间", hidden = true)
    private long currentTimeMillis;

}