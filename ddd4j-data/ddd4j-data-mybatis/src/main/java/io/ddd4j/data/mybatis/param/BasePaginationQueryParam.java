package io.ddd4j.data.mybatis.param;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 分页查询参数基类（MyBatis Plus 轨道）。
 *
 * <p>Pagination and query request objects belong to data adapters, not
 * {@code ddd4j-core} domain contracts.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public abstract class BasePaginationQueryParam extends BaseTimeRangeQueryParam {

    @Schema(example = "1", description = "当前页码")
    @Min(value = 1, message = "最小页码不能小于1")
    private int pageNo = 1;

    @Schema(example = "15", description = "每页记录数")
    @Min(value = 2, message = "每页至少2条数据")
    private int limit = 15;

    @Schema(description = "排序信息")
    private List<OrderItem> orders;

}
