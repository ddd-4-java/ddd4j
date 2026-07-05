package io.ddd4j.sample.spring.goods.web;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.api.R;
import io.ddd4j.sample.spring.goods.application.GoodsApplicationService;
import io.ddd4j.sample.spring.goods.domain.Goods;
import io.ddd4j.sample.spring.goods.domain.GoodsQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品 CQRS 读侧控制器（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>与 {@link GoodsController}（写侧）分离，演示 CQRS 模式：
 * <ul>
 *   <li>写侧：POST /api/goods、PUT /api/goods/{id} 等修改操作</li>
 *   <li>读侧：GET /api/goods/page、GET /api/goods/list、GET /api/goods/count 充血查询</li>
 * </ul>
 *
 * <p>读侧端点直接接收 {@link GoodsQuery}，由 Spring MVC 绑定字段后调用
 * {@code query.page()} / {@code query.list()} / {@code query.count()}。
 * 演示 ddd4j 的"Query 充血"能力：业务侧无需编写额外的分页 DTO。
 *
 * <p>REST 端点：
 * <ul>
 *   <li>GET /api/goods/page    充血分页查询（按条件 + 排序 + 分页）</li>
 *   <li>GET /api/goods/list    充血列表查询（按条件 + 排序）</li>
 *   <li>GET /api/goods/count   充血计数（按条件）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
@RequestMapping("/api/goods")
public class GoodsQueryController {

    private final GoodsApplicationService applicationService;

    /**
     * 构造函数（由 Spring 注入）。
     *
     * @param applicationService 商品应用服务
     */
    @Autowired
    public GoodsQueryController(GoodsApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 充血分页查询。
     *
     * <p>请求示例：{@code GET /api/goods/page?status=ON_SALE&current=1&size=20&orderBys=createTime_DESC}
     *
     * @param query 商品查询对象
     * @return 分页结果
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
     *
     * <p>请求示例：{@code GET /api/goods/list?status=ON_SALE&orderBys=id_ASC}
     *
     * @param query 商品查询对象
     * @return 商品列表
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
     *
     * <p>请求示例：{@code GET /api/goods/count?status=ON_SALE}
     *
     * @param query 商品查询对象
     * @return 总数
     */
    @GetMapping("/count")
    public R<Long> count(GoodsQuery query) {
        if (query == null) {
            query = new GoodsQuery();
        }
        return R.ok(applicationService.countQuery(query));
    }
}
