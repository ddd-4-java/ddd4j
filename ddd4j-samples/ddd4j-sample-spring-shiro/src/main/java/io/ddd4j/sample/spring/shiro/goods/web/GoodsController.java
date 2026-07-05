package io.ddd4j.sample.spring.shiro.goods.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.spring.shiro.goods.application.GoodsApplicationService;
import io.ddd4j.sample.spring.shiro.goods.domain.Goods;
import io.ddd4j.sample.spring.shiro.goods.domain.GoodsId;
import io.ddd4j.sample.spring.shiro.goods.domain.GoodsStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 商品 REST 控制器（写侧）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
@RequestMapping("/api/goods")
public class GoodsController {

    private final GoodsApplicationService applicationService;

    public GoodsController(GoodsApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 创建商品。
     *
     * <pre>
     * POST /api/goods
     * { "code": "SKU-001", "name": "iPhone 15", "price": 5999.00, "stock": 100 }
     * </pre>
     */
    @PostMapping
    public R<Goods> create(@RequestBody CreateGoodsRequest request) {
        Goods product = applicationService.create(
                request.code(), request.name(), request.price(), request.stock());
        return R.ok(product);
    }

    /**
     * 更新商品。
     */
    @PutMapping("/{id}")
    public R<Goods> update(@PathVariable Long id, @RequestBody UpdateGoodsRequest request) {
        Goods product = applicationService.update(
                GoodsId.of(id), request.name(), request.price());
        return R.ok(product);
    }

    /**
     * 调整商品状态。
     */
    @PutMapping("/{id}/status")
    public R<Goods> changeStatus(@PathVariable Long id, @RequestParam GoodsStatus status) {
        Goods product = applicationService.changeStatus(GoodsId.of(id), status);
        return R.ok(product);
    }

    /**
     * 删除商品（软删）。
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        applicationService.delete(GoodsId.of(id));
        return R.ok();
    }

    /**
     * 按 ID 查询商品。
     */
    @GetMapping("/{id}")
    public R<Goods> getById(@PathVariable Long id) {
        return R.ok(applicationService.getById(GoodsId.of(id)));
    }

    /**
     * 按编码查询商品。
     */
    @GetMapping("/by-code")
    public R<Goods> getByCode(@RequestParam String code) {
        return R.ok(applicationService.getByCode(code));
    }

    /** 创建商品请求。 */
    public record CreateGoodsRequest(String code, String name, BigDecimal price, Integer stock) {
    }

    /** 更新商品请求。 */
    public record UpdateGoodsRequest(String name, BigDecimal price) {
    }
}
