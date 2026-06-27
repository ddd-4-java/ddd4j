package io.ddd4j.core.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public abstract class BaseTimeRangeQueryParam {

    /**
     * 开始时间
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
    @Schema(description = "开始时间")
    private LocalDateTime beginTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    /**
     * 搜索关键字
     */
    @Schema(description = "搜索关键字")
    private String keywords;


}
