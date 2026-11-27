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
package io.ddd4j.sample.quarkus.shiro.goods.application;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.quarkus.shiro.goods.domain.*;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

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

    /** \u751f\u6210\u793a\u4f8b\u5546\u54c1\u4e3b\u952e\u4f7f\u7528\u7684\u8fdb\u7a0b\u5185\u5e8f\u5217\u3002 */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1000L);
    /** \u7528\u4e8e\u6301\u4e45\u5316\u53ca\u8bfb\u53d6\u793a\u4f8b\u5546\u54c1\u6a21\u578b\u7684\u4ed3\u50a8\u3002 */
    private final GoodsRepository repository;

    /**
     * 创建商品应用服务。
     *
     * @param repository 商品仓储
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
     * @param stock 库存数量
     * @return 创建后的商品
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
        return repository.save(goods);
    }

    /**
     * 更新商品基本信息。
     *
     * @param id    商品标识
     * @param name  商品名称
     * @param price 商品价格
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
        return repository.save(goods);
    }

    /**
     * 调整商品状态。
     *
     * @param id     商品标识
     * @param status 商品状态
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
        return repository.save(goods);
    }

    /**
     * 软删除商品。
     *
     * @param id 商品标识
     */
    public void delete(GoodsId id) {
        Goods goods = repository.findById(id.value())
                .orElseThrow(() -> new BizRuntimeException("goods.not.found", "goods not found: " + id));
        goods.setStatus(GoodsStatus.DELETED);
        goods.setUpdateTime(LocalDateTime.now());
        repository.save(goods);
    }

    /**
     * 按标识读取商品。
     *
     * @param id 商品标识
     * @return 商品
     */
    public Goods getById(GoodsId id) {
        return repository.findById(id.value())
                .orElseThrow(() -> new BizRuntimeException("goods.not.found", "goods not found: " + id));
    }

    /**
     * 按编码读取商品。
     *
     * @param code 商品编码
     * @return 商品
     */
    public Goods getByCode(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new BizRuntimeException("goods.not.found", "goods code not found: " + code));
    }

    /**
     * 按查询条件分页读取商品。
     *
     * @param query 商品查询条件
     * @return 商品分页结果
     */
    public Page<Goods> pageQuery(GoodsQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return query.page();
    }

    /**
     * 按查询条件列出商品。
     *
     * @param query 商品查询条件
     * @return 商品列表
     */
    public List<Goods> listQuery(GoodsQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return query.list();
    }

    // ========================= 私有校验方法 =========================

    /**
     * 按查询条件统计商品。
     *
     * @param query 商品查询条件
     * @return 商品数量
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
            throw new BizRuntimeException("goods.code.invalid", "code must not be blank");
        }
    }

    /**
     * \u68c0\u67e5\u5546\u54c1\u540d\u79f0\u662f\u5426\u6ee1\u8db3\u5199\u5165\u89c4\u5219\u3002
     * @param name \u5f85\u68c0\u67e5\u7684\u5546\u54c1\u5c5e\u6027
     */
    private void validateName(String name) {
        if (StrKit.isBlank(name)) {
            throw new BizRuntimeException("goods.name.invalid", "name must not be blank");
        }
    }

    /**
     * \u68c0\u67e5\u5546\u54c1\u4ef7\u683c\u662f\u5426\u6ee1\u8db3\u5199\u5165\u89c4\u5219\u3002
     * @param price \u5f85\u68c0\u67e5\u7684\u5546\u54c1\u5c5e\u6027
     */
    private void validatePrice(BigDecimal price) {
        if (Objects.isNull(price) || price.signum() < 0) {
            throw new BizRuntimeException("goods.price.invalid", "price must be >= 0");
        }
    }

    /**
     * \u9012\u589e\u5f53\u524d\u793a\u4f8b\u8fdb\u7a0b\u7684\u5546\u54c1\u4e3b\u952e\u5e8f\u5217\u3002
     * @return \u65b0\u751f\u6210\u7684\u5546\u54c1\u4e3b\u952e
     */
    private Long nextId() {
        return ID_GENERATOR.incrementAndGet();
    }
}
