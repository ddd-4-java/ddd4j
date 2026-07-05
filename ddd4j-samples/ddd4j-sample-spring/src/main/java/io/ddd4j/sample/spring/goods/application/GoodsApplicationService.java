package io.ddd4j.sample.spring.goods.application;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.sample.spring.goods.domain.Goods;
import io.ddd4j.sample.spring.goods.domain.GoodsId;
import io.ddd4j.sample.spring.goods.domain.GoodsQuery;
import io.ddd4j.sample.spring.goods.domain.GoodsRepository;
import io.ddd4j.sample.spring.goods.domain.GoodsStatus;
import io.ddd4j.spring.annotation.ApplicationService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 商品应用服务（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>本应用服务刻意保持"轻量"：直接编排 {@link GoodsRepository} 完成 CRUD，
 * 不维护聚合内的状态机、不触发领域事件、不通过充血方法（{@code goods.save()}）调用。
 * 业务校验（如价格非负、状态合法）保留在服务层而非聚合内。
 *
 * <p>与同包 {@code order} 子模块的 {@code OrderApplicationService} 对比：
 * <ul>
 *   <li><b>Order（充血）</b>：所有状态变更通过 {@code order.pay()} 等聚合方法，
 *       状态机、不变量、事件全部下沉到聚合根</li>
 *   <li><b>Goods（轻量）</b>：状态变更由服务直接设置字段，
 *       适合简单业务场景（CRUD 为主，无复杂业务规则）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@ApplicationService
public class GoodsApplicationService {

    private final GoodsRepository repository;

    /**
     * 构造函数（由 Spring 注入）。
     *
     * @param repository 商品仓储（不可为 null）
     */
    public GoodsApplicationService(GoodsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 创建商品。
     *
     * @param code  商品编码
     * @param name  商品名称
     * @param price 商品价格
     * @param stock 初始库存
     * @return 创建的商品
     * @throws BizRuntimeException 当商品编码已存在或参数非法时
     */
    public Goods create(String code, String name, BigDecimal price, Integer stock) {
        validateCode(code);
        validateName(name);
        validatePrice(price);
        if (Objects.isNull(stock) || stock < 0) {
            throw new BizRuntimeException("goods.stock.invalid", "stock must be >= 0");
        }
        repository.findByCode(code).ifPresent(existing -> {
            throw new BizRuntimeException("goods.code.duplicate", "goods code already exists: " + code);
        });

        LocalDateTime now = LocalDateTime.now();
        Goods goods = Goods.builder()
                .id(nextId())
                .code(code)
                .name(name)
                .price(price)
                .stock(stock)
                .status(GoodsStatus.DRAFT)
                .createTime(now)
                .updateTime(now)
                .build();
        log.info("Created goods: id={}, code={}, name={}", goods.id(), goods.getCode(), goods.getName());
        return repository.save(goods);
    }

    /**
     * 更新商品基本信息。
     *
     * @param id    商品 ID
     * @param name  新商品名称
     * @param price 新商品价格
     * @return 更新后的商品
     */
    public Goods update(GoodsId id, String name, BigDecimal price) {
        Goods goods = repository.findById(id.value())
                .orElseThrow(() -> new BizRuntimeException("goods.not.found", "goods not found: " + id));
        if (GoodsStatus.DELETED.equals(goods.getStatus())) {
            throw new BizRuntimeException("goods.deleted", "deleted goods cannot be updated");
        }
        validateName(name);
        validatePrice(price);
        goods.setName(name);
        goods.setPrice(price);
        goods.setUpdateTime(LocalDateTime.now());
        log.info("Updated goods: id={}, newName={}, newPrice={}", goods.id(), goods.getName(), goods.getPrice());
        return repository.save(goods);
    }

    /**
     * 调整商品状态。
     *
     * @param id     商品 ID
     * @param status 新状态
     * @return 更新后的商品
     */
    public Goods changeStatus(GoodsId id, GoodsStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        Goods goods = repository.findById(id.value())
                .orElseThrow(() -> new BizRuntimeException("goods.not.found", "goods not found: " + id));
        if (GoodsStatus.DELETED.equals(goods.getStatus())) {
            throw new BizRuntimeException("goods.deleted", "deleted goods cannot change status");
        }
        goods.setStatus(status);
        goods.setUpdateTime(LocalDateTime.now());
        log.info("Changed goods status: id={}, newStatus={}", goods.id(), goods.getStatus());
        return repository.save(goods);
    }

    /**
     * 软删除商品。
     *
     * @param id 商品 ID
     */
    public void delete(GoodsId id) {
        Goods goods = repository.findById(id.value())
                .orElseThrow(() -> new BizRuntimeException("goods.not.found", "goods not found: " + id));
        goods.setStatus(GoodsStatus.DELETED);
        goods.setUpdateTime(LocalDateTime.now());
        repository.save(goods);
        log.info("Soft-deleted goods: id={}", goods.id());
    }

    /**
     * 按 ID 查询商品。
     *
     * @param id 商品 ID
     * @return 商品
     */
    public Goods getById(GoodsId id) {
        return repository.findById(id.value())
                .orElseThrow(() -> new BizRuntimeException("goods.not.found", "goods not found: " + id));
    }

    /**
     * 按编码查询商品。
     *
     * @param code 商品编码
     * @return 商品
     */
    public Goods getByCode(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new BizRuntimeException("goods.not.found", "goods code not found: " + code));
    }

    /**
     * 充血查询：按条件分页查询。
     *
     * <p>直接使用 {@link GoodsQuery#page()} 触发仓储查询，演示 ddd4j 的"Query 充血"能力。
     * 业务侧无需关心底层是 MyBatis、JPA 还是内存。
     *
     * @param query 商品查询对象
     * @return 分页结果
     */
    public Page<Goods> pageQuery(GoodsQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return query.page();
    }

    /**
     * 充血查询：按条件列表查询。
     *
     * @param query 商品查询对象
     * @return 商品列表
     */
    public List<Goods> listQuery(GoodsQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return query.list();
    }

    /**
     * 充血查询：按条件统计。
     *
     * @param query 商品查询对象
     * @return 总数
     */
    public long countQuery(GoodsQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return query.count();
    }

    // ========================= 私有校验方法 =========================

    private void validateCode(String code) {
        if (Objects.isNull(code) || code.isBlank()) {
            throw new BizRuntimeException("goods.code.invalid", "code must not be blank");
        }
    }

    private void validateName(String name) {
        if (Objects.isNull(name) || name.isBlank()) {
            throw new BizRuntimeException("goods.name.invalid", "name must not be blank");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (Objects.isNull(price) || price.signum() < 0) {
            throw new BizRuntimeException("goods.price.invalid", "price must be >= 0");
        }
    }

    /**
     * 生成下一个主键 ID（演示用，生产环境应使用分布式 ID）。
     *
     * @return 新的主键
     */
    private Long nextId() {
        return ID_GENERATOR.incrementAndGet();
    }

    /** 内存模式下的 ID 生成器（实际生产应使用雪花算法等） */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1000L);
}
