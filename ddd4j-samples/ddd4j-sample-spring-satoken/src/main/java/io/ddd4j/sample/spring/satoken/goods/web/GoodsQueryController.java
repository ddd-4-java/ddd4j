package io.ddd4j.sample.spring.satoken.goods.web;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.api.R;
import io.ddd4j.sample.spring.satoken.goods.application.GoodsApplicationService;
import io.ddd4j.sample.spring.satoken.goods.domain.Goods;
import io.ddd4j.sample.spring.satoken.goods.domain.GoodsQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品充血查询控制器（读侧）。
 *
 * <p>直接接收 {@link GoodsQuery}，由 Spring MVC 绑定字段后调用 {@code query.page()} /
 * {@code query.list()} 等充血查询方法。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
@RequestMapping("/api/goods")
public class GoodsQueryController {

    private final GoodsApplicationService applicationService;

    public GoodsQueryController(GoodsApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 充血分页查询。
     */
    @GetMapping("/page")
    public R<Page<Goods>> page(GoodsQuery query) {
        if (query == null) {
            query = new GoodsQuery();
        }
        return R.ok(applicationService.pageQuery(query));
    }

    /**
     * 充血列表查询。
     */
    @GetMapping("/list")
    public R<List<Goods>> list(GoodsQuery query) {
        if (query == null) {
            query = new GoodsQuery();
        }
        return R.ok(applicationService.listQuery(query));
    }

    /**
     * 充血计数。
     */
    @GetMapping("/count")
    public R<Long> count(GoodsQuery query) {
        if (query == null) {
            query = new GoodsQuery();
        }
        return R.ok(applicationService.countQuery(query));
    }
}
