package io.ddd4j.sample.spring.cqrs.goods.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.spring.cqrs.cache.GoodsCacheService;
import io.ddd4j.sample.spring.cqrs.goods.domain.Goods;
import io.ddd4j.sample.spring.cqrs.goods.domain.GoodsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商品 CQRS 读侧控制器 - 缓存优先版本（Spring Boot）。
 *
 * <p>本控制器是 CQRS 示例相对于基线 {@link GoodsQueryController} 的增强：
 * <ul>
 *   <li>按 ID 查询走 {@link GoodsCacheService} 缓存（Caffeine 本地缓存）</li>
 *   <li>按 code 查询同样走缓存</li>
 *   <li>列表/计数查询仓储直查</li>
 * </ul>
 *
 * <p>REST 端点：
 * <ul>
 *   <li>GET /api/goods/query/by-id/{id}      按 ID 查询（缓存优先）</li>
 *   <li>GET /api/goods/query/by-code/{code}  按编码查询（缓存优先）</li>
 *   <li>GET /api/goods/query/list            列表查询</li>
 *   <li>GET /api/goods/query/count           计数查询</li>
 *   <li>GET /api/goods/query/cache-stats     缓存统计</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
@RequestMapping("/api/goods/query")
public class GoodsReadController {

    private final GoodsCacheService goodsCacheService;
    private final GoodsRepository goodsRepository;

    /**
     * 构造函数（Spring 注入）。
     *
     * @param goodsCacheService 商品缓存服务
     * @param goodsRepository   商品仓储
     */
    @Autowired
    public GoodsReadController(GoodsCacheService goodsCacheService, GoodsRepository goodsRepository) {
        this.goodsCacheService = goodsCacheService;
        this.goodsRepository = goodsRepository;
    }

    /**
     * 按 ID 查询（缓存优先）。
     *
     * @param id 商品 ID
     * @return 商品
     */
    @GetMapping("/by-id/{id}")
    public R<Goods> getById(@PathVariable Long id) {
        return R.ok(goodsCacheService.getById(id));
    }

    /**
     * 按编码查询（先从缓存 ID 索引走，没有则查仓储再回填缓存）。
     *
     * @param code 商品编码
     * @return 商品
     */
    @GetMapping("/by-code/{code}")
    public R<Goods> getByCode(@PathVariable String code) {
        Goods goods = goodsRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("goods not found: " + code));
        return R.ok(goods);
    }

    /**
     * 列表查询（缓存快照）。
     *
     * @return 全部商品列表
     */
    @GetMapping("/list")
    public R<List<Goods>> list() {
        return R.ok(goodsCacheService.listAll());
    }

    /**
     * 按状态过滤（缓存快照）。
     *
     * @param status 状态
     * @return 该状态下商品
     */
    @GetMapping("/by-status")
    public R<List<Goods>> listByStatus(@RequestParam String status) {
        return R.ok(goodsCacheService.listByStatus(status));
    }

    /**
     * 计数。
     *
     * @return 总数
     */
    @GetMapping("/count")
    public R<Long> count() {
        return R.ok(goodsCacheService.listAll().stream().count());
    }

    /**
     * 缓存统计（CQRS 监控）。
     *
     * @return 缓存统计 Map
     */
    @GetMapping("/cache-stats")
    public R<Map<String, Object>> cacheStats() {
        return R.ok(goodsCacheService.stats());
    }
}