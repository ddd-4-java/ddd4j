package io.ddd4j.core.param;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public abstract class BasePaginationQueryParam extends BaseTimeRangeQueryParam {

    /**
     * 当前页码
     */
    @Schema(example = "1", description = "当前页码")
    @Min(value = 1, message = "最小页码不能小于1")
    private int pageNo = 1;

    /**
     * 每页记录数
     */
    @Schema(example = "15", description = "每页记录数")
    @Min(value = 2, message = "每页至少2条数据")
    private int limit = 15;

    /**
     * 排序信息
     */
    @Schema(description = "排序信息")
    private List<OrderItem> orders;

}
