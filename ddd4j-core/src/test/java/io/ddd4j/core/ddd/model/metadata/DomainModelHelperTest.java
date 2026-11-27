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
package io.ddd4j.core.ddd.model.metadata;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DomainModelHelper} 映射隔离测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class DomainModelHelperTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>DomainModelHelper.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        DomainModelHelper.clear();
    }

    /** \u9a8c\u8bc1<code>mysqlMapping.getPoColumn("status")</code> \u7b49\u4e8e <code>"mysql_status"</code>\uff0c<code>jpaMapping.getPoColumn("status")</code> \u7b49\u4e8e <code>"jpa_status"</code>\u3002 */
    @Test
    void mappingCacheShouldBeIsolatedByPersistenceType() {
        DomainModelInfo<Order> mysqlMapping = DomainModelHelper.getModelInfo(
                Order.class, MysqlOrderPO.class, property -> "mysql_" + property);
        DomainModelInfo<Order> jpaMapping = DomainModelHelper.getModelInfo(
                Order.class, JpaOrderEntity.class, property -> "jpa_" + property);

        assertThat(mysqlMapping.getPoColumn("status")).isEqualTo("mysql_status");
        assertThat(jpaMapping.getPoColumn("status")).isEqualTo("jpa_status");
    }

    /** \u9a8c\u8bc1<code>properties</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"status"</code>\u3002 */
    @Test
    void metadataShouldExcludeStaticTransientAndSyntheticFields() {
        DomainModelInfo<Order> mapping = DomainModelHelper.getModelInfo(Order.class);

        Set<String> properties = mapping.getFieldList().stream()
                .map(DomainFieldInfo::getProperty)
                .collect(Collectors.toSet());

        assertThat(properties).containsExactly("status");
    }

    /** \u9a8c\u8bc1<code>mysqlAgain</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>mysql</code>\uff0c<code>jpa</code> \u6307\u5411\u4e0d\u540c\u5b9e\u4f8b <code>mysql</code>\u3002 */
    @Test
    void cacheShouldReuseMappingByContextAndRemoveAllContextsForModel() {
        DomainModelInfo<Order> mysql = DomainModelHelper.getModelInfo(
                Order.class, MysqlOrderPO.class, property -> "mysql_" + property);
        DomainModelInfo<Order> mysqlAgain = DomainModelHelper.getModelInfo(
                Order.class, MysqlOrderPO.class, property -> "other_" + property);
        DomainModelInfo<Order> jpa = DomainModelHelper.getModelInfo(
                Order.class, JpaOrderEntity.class, property -> "jpa_" + property);

        assertThat(mysqlAgain).isSameAs(mysql);
        assertThat(jpa).isNotSameAs(mysql);

        DomainModelHelper.remove(Order.class);

        assertThat(DomainModelHelper.getModelInfo(Order.class, MysqlOrderPO.class,
                property -> "new_" + property)).isNotSameAs(mysql);
        assertThat(DomainModelHelper.getModelInfo(Order.class, JpaOrderEntity.class,
                property -> "new_" + property)).isNotSameAs(jpa);
    }

    /** \u9a8c\u8bc1<code>DomainModelHelper.getModelInfo(null)</code>\u4e3a\u7a7a\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void invalidModelShouldReturnNullAndMissingPersistenceTypeShouldFail() {
        assertThat(DomainModelHelper.getModelInfo(null)).isNull();
        assertThatThrownBy(() -> DomainModelHelper.getModelInfo(
                Order.class, null, property -> property))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("persistenceType must not be null");
    }

    /**
     * 订单领域模型测试夹具，包含静态、瞬态与普通字段以验证元数据过滤。
     */
    static final class Order {

        /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u6807\u8bc6\u6216\u540d\u79f0\u3002 */
        private static final String TYPE = "ORDER";
        /** \u6d4b\u8bd5\u8bb0\u5f55\u7684\u6846\u67b6\u8fd0\u884c\u6216\u5173\u95ed\u72b6\u6001\u3002 */
        private transient String runtimeState;
        /** \u6d4b\u8bd5\u66ff\u8eab\u8fd4\u56de\u3001\u6a21\u62df\u6216\u6355\u83b7\u7684\u72b6\u6001\u3002 */
        private String status;
    }

    /**
     * 关系型持久化对象测试夹具，用于区分按持久化类型隔离的映射缓存。
     */
    static final class MysqlOrderPO {
    }

    /**
     * 对象关系映射实体测试夹具，用于区分按持久化类型隔离的映射缓存。
     */
    static final class JpaOrderEntity {
    }
}
