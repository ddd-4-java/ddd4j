package io.ddd4j.data.mybatis.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Time range query parameter base for data-layer query objects.
 */
@Data
public abstract class BaseTimeRangeQueryParam {

    /**
     * Start time.
     */
    @Schema(description = "开始时间")
    private LocalDateTime beginTime;

    /**
     * End time.
     */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    /**
     * Search keywords.
     */
    @Schema(description = "搜索关键字")
    private String keywords;
}
