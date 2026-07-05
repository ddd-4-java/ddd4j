package io.ddd4j.sample.quarkus.cqrs.goods.domain;

import io.ddd4j.core.ddd.model.AggregateRoot;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 商品 PO 实体（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>本类与同包下 {@code ddd4j-sample-rich-model} 中 {@code Order} 聚合形成对比：
 * <ul>
 *   <li>{@code Order} 充血模型：状态机、不变量、事件全部下沉到聚合根方法（{@code pay()} / {@code ship()}）</li>
 *   <li>本类轻量 PO：仅包含数据字段与基本 getter/setter，
 *       业务校验由应用服务（{@code GoodsApplicationService}）统一编排</li>
 * </ul>
 *
 * <p>实体仍继承 {@link AggregateRoot}，因为 ddd4j 的 {@code Repository<M, ID>}
 * 约束仓储只针对聚合根——这是 ddd4j 唯一的领域模型基类。
 * 但本示例的 Goods 不会调用任何充血方法（如 {@code save()} / {@code delete()}），
 * 持久化由应用服务统一编排。
 *
 * <p>本类显式实现 {@code Serializable} 并提供 getter/setter，
 * 以保持 PO 风格（不依赖 Lombok），更贴近 Quarkus 生态习惯。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Goods extends AggregateRoot<Long> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商品主键 ID。
     */
    private Long id;
    /**
     * 商品编码（业务唯一键）。
     */
    private String code;
    /**
     * 商品名称。
     */
    private String name;
    /**
     * 商品价格。
     */
    private BigDecimal price;
    /**
     * 库存数量。
     */
    private Integer stock;
    /**
     * 商品状态。
     */
    private GoodsStatus status;
    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
    /**
     * 最后更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 无参构造器（持久化框架需要）。
     */
    public Goods() {
    }

    /**
     * 全参构造器。
     */
    public Goods(Long id, String code, String name, BigDecimal price, Integer stock,
                   GoodsStatus status, LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    @Override
    public Long id() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public GoodsStatus getStatus() {
        return status;
    }

    public void setStatus(GoodsStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Goods)) {
            return false;
        }
        Goods product = (Goods) o;
        return Objects.equals(id, product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Goods{id=" + id + ", code='" + code + "', name='" + name + "', price=" + price
                + ", stock=" + stock + ", status=" + status + '}';
    }
}
