package io.ddd4j.sample.spring.shiro.goods.application;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.sample.spring.shiro.goods.domain.Goods;
import io.ddd4j.sample.spring.shiro.goods.domain.GoodsId;
import io.ddd4j.sample.spring.shiro.goods.domain.GoodsQuery;
import io.ddd4j.sample.spring.shiro.goods.domain.GoodsRepository;
import io.ddd4j.sample.spring.shiro.goods.domain.GoodsStatus;
import io.ddd4j.spring.annotation.ApplicationService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 商品应用服务（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>该应用服务在三个 Spring Auth 示例（satoken / shiro / security）中<b>完全一致</b>，
 * 用于演示"业务代码零框架耦合"。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
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
     */
    public Goods create(String code, String name, BigDecimal price, Integer stock) {
        validateCode(code);
        validateName(name);
        validatePrice(price);
        if (Objects.isNull(stock) || stock < 0) {
            throw new BizRuntimeException("product.stock.invalid", "stock must be >= 0");
        }
        repository.findByCode(code).ifPresent(existing -> {
            throw new BizRuntimeException("product.code.duplicate", "product code already exists: " + code);
        });

        LocalDateTime now = LocalDateTime.now();
        Goods product = Goods.builder()
                .id(nextId())
                .code(code)
                .name(name)
                .price(price)
                .stock(stock)
                .status(GoodsStatus.DRAFT)
                .createTime(now)
                .updateTime(now)
                .build();
        return repository.save(product);
    }

    /**
     * 更新商品基本信息。
     */
    public Goods update(GoodsId id, String name, BigDecimal price) {
        Goods product = repository.findById(id.value())
                .orElseThrow(() -> new BizRuntimeException("product.not.found", "product not found: " + id));
        if (GoodsStatus.DELETED.equals(product.getStatus())) {
            throw new BizRuntimeException("product.deleted", "deleted product cannot be updated");
        }
        validateName(name);
        validatePrice(price);
        product.setName(name);
        product.setPrice(price);
        product.setUpdateTime(LocalDateTime.now());
        return repository.save(product);
    }

    /**
     * 调整商品状态。
     */
    public Goods changeStatus(GoodsId id, GoodsStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        Goods product = repository.findById(id.value())
                .orElseThrow(() -> new BizRuntimeException("product.not.found", "product not found: " + id));
        if (GoodsStatus.DELETED.equals(product.getStatus())) {
            throw new BizRuntimeException("product.deleted", "deleted product cannot change status");
        }
        product.setStatus(status);
        product.setUpdateTime(LocalDateTime.now());
        return repository.save(product);
    }

    /**
     * 软删除商品。
     */
    public void delete(GoodsId id) {
        Goods product = repository.findById(id.value())
                .orElseThrow(() -> new BizRuntimeException("product.not.found", "product not found: " + id));
        product.setStatus(GoodsStatus.DELETED);
        product.setUpdateTime(LocalDateTime.now());
        repository.save(product);
    }

    /**
     * 按 ID 查询商品。
     */
    public Goods getById(GoodsId id) {
        return repository.findById(id.value())
                .orElseThrow(() -> new BizRuntimeException("product.not.found", "product not found: " + id));
    }

    /**
     * 按编码查询商品。
     */
    public Goods getByCode(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new BizRuntimeException("product.not.found", "product code not found: " + code));
    }

    /**
     * 充血查询：按条件分页查询。
     */
    public Page<Goods> pageQuery(GoodsQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return query.page();
    }

    /**
     * 充血查询：按条件列表查询。
     */
    public List<Goods> listQuery(GoodsQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return query.list();
    }

    /**
     * 充血查询：按条件统计。
     */
    public long countQuery(GoodsQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return query.count();
    }

    // ========================= 私有校验方法 =========================

    private void validateCode(String code) {
        if (Objects.isNull(code) || code.isBlank()) {
            throw new BizRuntimeException("product.code.invalid", "code must not be blank");
        }
    }

    private void validateName(String name) {
        if (Objects.isNull(name) || name.isBlank()) {
            throw new BizRuntimeException("product.name.invalid", "name must not be blank");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (Objects.isNull(price) || price.signum() < 0) {
            throw new BizRuntimeException("product.price.invalid", "price must be >= 0");
        }
    }

    /**
     * 生成下一个主键 ID（演示用，生产环境应使用分布式 ID）。
     */
    private Long nextId() {
        return ID_GENERATOR.incrementAndGet();
    }

    /** 内存模式下的 ID 生成器（实际生产应使用雪花算法等） */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1000L);
}
