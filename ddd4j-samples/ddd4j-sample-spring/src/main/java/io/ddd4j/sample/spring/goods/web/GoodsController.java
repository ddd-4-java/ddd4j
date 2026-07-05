package io.ddd4j.sample.spring.goods.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.spring.goods.application.GoodsApplicationService;
import io.ddd4j.sample.spring.goods.domain.Goods;
import io.ddd4j.sample.spring.goods.domain.GoodsId;
import io.ddd4j.sample.spring.goods.domain.GoodsStatus;
import io.ddd4j.sample.spring.goods.web.dto.CreateGoodsRequest;
import io.ddd4j.sample.spring.goods.web.dto.UpdateGoodsRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品 REST 写侧控制器（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>所有响应统一使用 ddd4j 的 {@link R}{@code <T>} 包装：
 * <ul>
 *   <li>{@code R.ok(data)} - 成功响应</li>
 *   <li>{@code R.fail(code, msg)} - 失败响应</li>
 * </ul>
 *
 * <p>请求体使用 record 形式 DTO 接收，简单业务场景下"轻量"是优势。
 *
 * <p>REST 端点：
 * <ul>
 *   <li>POST   /api/goods               创建商品</li>
 *   <li>PUT    /api/goods/{id}          更新商品</li>
 *   <li>PUT    /api/goods/{id}/status   调整商品状态</li>
 *   <li>DELETE /api/goods/{id}          删除商品（软删）</li>
 *   <li>GET    /api/goods/{id}          按 ID 查询商品</li>
 *   <li>GET    /api/goods/by-code       按编码查询商品</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
@RequestMapping("/api/goods")
public class GoodsController {

    private final GoodsApplicationService applicationService;

    /**
     * 构造函数（由 Spring 注入）。
     *
     * @param applicationService 商品应用服务
     */
    @Autowired
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
     *
     * @param request 创建请求
     * @return 创建的商品
     */
    @PostMapping
    public R<Goods> create(@RequestBody CreateGoodsRequest request) {
        Goods goods = applicationService.create(
                request.code(), request.name(), request.price(), request.stock());
        return R.ok("goods created", goods);
    }

    /**
     * 更新商品。
     *
     * <pre>
     * PUT /api/goods/{id}
     * { "name": "iPhone 15 Pro", "price": 7999.00 }
     * </pre>
     *
     * @param id      商品 ID
     * @param request 更新请求
     * @return 更新后的商品
     */
    @PutMapping("/{id}")
    public R<Goods> update(@PathVariable Long id, @RequestBody UpdateGoodsRequest request) {
        Goods goods = applicationService.update(
                GoodsId.of(id), request.name(), request.price());
        return R.ok("goods updated", goods);
    }

    /**
     * 调整商品状态。
     *
     * <pre>
     * PUT /api/goods/{id}/status?status=ON_SALE
     * </pre>
     *
     * @param id     商品 ID
     * @param status 新状态
     * @return 更新后的商品
     */
    @PutMapping("/{id}/status")
    public R<Goods> changeStatus(@PathVariable Long id, @RequestParam GoodsStatus status) {
        Goods goods = applicationService.changeStatus(GoodsId.of(id), status);
        return R.ok("goods status changed", goods);
    }

    /**
     * 删除商品（软删）。
     *
     * @param id 商品 ID
     * @return 成功响应
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        applicationService.delete(GoodsId.of(id));
        return R.ok("goods deleted");
    }

    /**
     * 按 ID 查询商品。
     *
     * @param id 商品 ID
     * @return 商品
     */
    @GetMapping("/{id}")
    public R<Goods> getById(@PathVariable Long id) {
        return R.ok(applicationService.getById(GoodsId.of(id)));
    }

    /**
     * 按编码查询商品。
     *
     * @param code 商品编码
     * @return 商品
     */
    @GetMapping("/by-code")
    public R<Goods> getByCode(@RequestParam String code) {
        return R.ok(applicationService.getByCode(code));
    }
}
