/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.quarkus.goods.application;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.quarkus.goods.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 商品应用服务（第三轨：Model/Query 快速 CRUD 模式，Quarkus CDI 风格）。
 *
 * <p>本应用服务刻意保持"轻量"：直接编排 {@link GoodsRepository} 完成 CRUD，
 * 不维护聚合内的状态机、不触发领域事件、不通过充血方法（{@code product.save()}）调用。
 * 业务校验（如价格非负、状态合法）保留在服务层而非聚合内。
 *
 * <p>与同项目 {@code order} 子包中的 {@code OrderApplicationService} 对比：
 * <ul>
 *   <li>Order（第二轨）：所有状态变更通过 {@code order.pay()} 等聚合方法，
 *       状态机、不变量、事件全部下沉到聚合根</li>
 *   <li>Goods（第三轨）：状态变更由服务直接设置字段，
 *       适合简单业务场景（CRUD 为主，无复杂业务规则）</li>
 * </ul>
 *
 * <p>本服务由 Quarkus CDI 管理（{@link ApplicationScoped}），
 * 通过构造器注入（{@code @Inject}）{@link GoodsRepository}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class GoodsApplicationService {

    /**
     * 内存模式下的 ID 生成器（实际生产应使用雪花算法等）
     */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1000L);
    /** \u7528\u4e8e\u6301\u4e45\u5316\u53ca\u8bfb\u53d6\u793a\u4f8b\u5546\u54c1\u6a21\u578b\u7684\u4ed3\u50a8\u3002 */
    private final GoodsRepository repository;

    /**
     * CDI 构造器注入（推荐方式，便于单测与容器生命周期解耦）。
     *
     * @param repository 商品仓储（不可为 null）
     */
    @Inject
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
            throw new BizRuntimeException("product.stock.invalid", "stock must be >= 0");
        }
        repository.findByCode(code).ifPresent(existing -> {
            throw new BizRuntimeException("product.code.duplicate", "product code already exists: " + code);
        });

        LocalDateTime now = LocalDateTime.now();
        Goods product = new Goods(
                nextId(),
                code,
                name,
                price,
                stock,
                GoodsStatus.DRAFT,
                now,
                now);
        return repository.save(product);
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
     *
     * @param id     商品 ID
     * @param status 新状态
     * @return 更新后的商品
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
     *
     * @param id 商品 ID
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
     *
     * @param id 商品 ID
     * @return 商品（不存在时由业务异常抛出）
     */
    public Goods getById(GoodsId id) {
        return repository.findById(id.value())
                .orElseThrow(() -> new BizRuntimeException("product.not.found", "product not found: " + id));
    }

    /**
     * 按编码查询商品。
     *
     * @param code 商品编码
     * @return 商品
     */
    public Goods getByCode(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new BizRuntimeException("product.not.found", "product code not found: " + code));
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

    // ========================= 私有校验方法 =========================

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

    /**
     * \u68c0\u67e5\u5546\u54c1\u7f16\u7801\u662f\u5426\u6ee1\u8db3\u521b\u5efa\u6216\u67e5\u8be2\u89c4\u5219\u3002
     * @param code \u5f85\u68c0\u67e5\u7684\u5546\u54c1\u5c5e\u6027
     */
    private void validateCode(String code) {
        if (StrKit.isBlank(code)) {
            throw new BizRuntimeException("product.code.invalid", "code must not be blank");
        }
    }

    /**
     * \u68c0\u67e5\u5546\u54c1\u540d\u79f0\u662f\u5426\u6ee1\u8db3\u5199\u5165\u89c4\u5219\u3002
     * @param name \u5f85\u68c0\u67e5\u7684\u5546\u54c1\u5c5e\u6027
     */
    private void validateName(String name) {
        if (StrKit.isBlank(name)) {
            throw new BizRuntimeException("product.name.invalid", "name must not be blank");
        }
    }

    /**
     * \u68c0\u67e5\u5546\u54c1\u4ef7\u683c\u662f\u5426\u6ee1\u8db3\u5199\u5165\u89c4\u5219\u3002
     * @param price \u5f85\u68c0\u67e5\u7684\u5546\u54c1\u5c5e\u6027
     */
    private void validatePrice(BigDecimal price) {
        if (Objects.isNull(price) || price.signum() < 0) {
            throw new BizRuntimeException("product.price.invalid", "price must be >= 0");
        }
    }

    /**
     * \u751f\u6210\u4e0b\u4e00\u4e2a\u4e3b\u952e ID\uff08\u6f14\u793a\u7528\uff0c\u751f\u4ea7\u73af\u5883\u5e94\u4f7f\u7528\u5206\u5e03\u5f0f ID\uff09\u3002
     *
     * @return \u9012\u589e\u540e\u7684\u793a\u4f8b\u5546\u54c1\u4e3b\u952e
     */
    private Long nextId() {
        return ID_GENERATOR.incrementAndGet();
    }
}
