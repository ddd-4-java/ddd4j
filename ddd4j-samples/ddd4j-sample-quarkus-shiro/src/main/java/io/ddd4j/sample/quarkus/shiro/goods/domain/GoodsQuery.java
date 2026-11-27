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
package io.ddd4j.sample.quarkus.shiro.goods.domain;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商品充血查询对象（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GoodsQuery extends Query<Goods> {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 1L;

    /**
     * 商品编码。
     */
    private String code;
    /**
     * 名称模糊匹配条件。
     */
    private String nameLike;
    /**
     * 商品状态。
     */
    private GoodsStatus status;
    /**
     * 最低价格。
     */
    private BigDecimal priceMin;
    /**
     * 最高价格。
     */
    private BigDecimal priceMax;

    /**
     * 设置排序条件。
     *
     * @param orderBys 排序字段
     * @return 当前查询对象
     */
    public GoodsQuery setOrderBys(String orderBys) {
        if (io.ddd4j.kit.lang.StrKit.isBlank(orderBys)) {
            return this;
        }
        for (String orderBy : orderBys.split(",")) {
            String[] tokens = orderBy.trim().split("_");
            if (tokens.length != 2) {
                continue;
            }
            boolean desc = "DESC".equalsIgnoreCase(tokens[1]);
            switch (tokens[0]) {
                case "id" -> applyOrder(desc, Goods::id);
                case "createTime" -> applyOrder(desc, Goods::getCreateTime);
                case "updateTime" -> applyOrder(desc, Goods::getUpdateTime);
                case "price" -> applyOrder(desc, Goods::getPrice);
                default -> {
                }
            }
        }
        return this;
    }

    /**
     * \u6309\u65b9\u5411\u4e3a\u5546\u54c1\u67e5\u8be2\u8ffd\u52a0\u9886\u57df\u5c5e\u6027\u6392\u5e8f\u6761\u4ef6\u3002
     * @param desc \u662f\u5426\u6309\u964d\u5e8f\u6392\u5217
     * @param property \u5546\u54c1\u6392\u5e8f\u6240\u4f7f\u7528\u7684\u9886\u57df\u5c5e\u6027\u5f15\u7528
     */
    private void applyOrder(boolean desc, io.ddd4j.kit.lang.SFunction<Goods, ?> property) {
        if (desc) {
            orderByDesc(property);
            return;
        }
        orderByAsc(property);
    }

    /**
     * 返回当前用例依赖的仓储。
     *
     * <p>此实现调用 <code>RepositoryRegistry.repository(Goods.class)</code>。</p>
     *
     * @return 实际返回<code>RepositoryRegistry.repository(Goods.class)</code>
     */
    @Override
    public Repository<Goods, Long> repository() {
        return RepositoryRegistry.repository(Goods.class);
    }
}
