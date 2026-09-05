package io.ddd4j.sample.quarkus.satoken.goods.application;

import io.ddd4j.kit.lang.StrKit;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.sample.quarkus.satoken.goods.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 商品应用服务（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class GoodsApplicationService {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(1000L);
    private final GoodsRepository repository;

    @Inject
    public GoodsApplicationService(GoodsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

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
        return repository.save(goods);
    }

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
        return repository.save(goods);
    }

    public Goods changeStatus(GoodsId id, GoodsStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        Goods goods = repository.findById(id.value())
                .orElseThrow(() -> new BizRuntimeException("goods.not.found", "goods not found: " + id));
        if (GoodsStatus.DELETED.equals(goods.getStatus())) {
            throw new BizRuntimeException("goods.deleted", "deleted goods cannot change status");
        }
        goods.setStatus(status);
        goods.setUpdateTime(LocalDateTime.now());
        return repository.save(goods);
    }

    public void delete(GoodsId id) {
        Goods goods = repository.findById(id.value())
                .orElseThrow(() -> new BizRuntimeException("goods.not.found", "goods not found: " + id));
        goods.setStatus(GoodsStatus.DELETED);
        goods.setUpdateTime(LocalDateTime.now());
        repository.save(goods);
    }

    public Goods getById(GoodsId id) {
        return repository.findById(id.value())
                .orElseThrow(() -> new BizRuntimeException("goods.not.found", "goods not found: " + id));
    }

    public Goods getByCode(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new BizRuntimeException("goods.not.found", "goods code not found: " + code));
    }

    public Page<Goods> pageQuery(GoodsQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return query.page();
    }

    public List<Goods> listQuery(GoodsQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return query.list();
    }

    // ========================= 私有校验方法 =========================

    public long countQuery(GoodsQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return query.count();
    }

    private void validateCode(String code) {
        if (StrKit.isBlank(code)) {
            throw new BizRuntimeException("goods.code.invalid", "code must not be blank");
        }
    }

    private void validateName(String name) {
        if (StrKit.isBlank(name)) {
            throw new BizRuntimeException("goods.name.invalid", "name must not be blank");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (Objects.isNull(price) || price.signum() < 0) {
            throw new BizRuntimeException("goods.price.invalid", "price must be >= 0");
        }
    }

    private Long nextId() {
        return ID_GENERATOR.incrementAndGet();
    }
}