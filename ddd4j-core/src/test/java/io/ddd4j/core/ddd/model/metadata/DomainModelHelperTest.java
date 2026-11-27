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

    /**
     * 测试结束后执行 <code>DomainModelHelper.clear()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        DomainModelHelper.clear();
    }

    /**
     * 验证<code>mysqlMapping.getPoColumn("status")</code> 等于 <code>"mysql_status"</code>，<code>jpaMapping.getPoColumn("status")</code> 等于 <code>"jpa_status"</code>。
     */
    @Test
    void mappingCacheShouldBeIsolatedByPersistenceType() {
        DomainModelInfo<Order> mysqlMapping = DomainModelHelper.getModelInfo(
                Order.class, MysqlOrderPO.class, property -> "mysql_" + property);
        DomainModelInfo<Order> jpaMapping = DomainModelHelper.getModelInfo(
                Order.class, JpaOrderEntity.class, property -> "jpa_" + property);

        assertThat(mysqlMapping.getPoColumn("status")).isEqualTo("mysql_status");
        assertThat(jpaMapping.getPoColumn("status")).isEqualTo("jpa_status");
    }

    /**
     * 验证<code>properties</code> 按顺序仅包含 <code>"status"</code>。
     */
    @Test
    void metadataShouldExcludeStaticTransientAndSyntheticFields() {
        DomainModelInfo<Order> mapping = DomainModelHelper.getModelInfo(Order.class);

        Set<String> properties = mapping.getFieldList().stream()
                .map(DomainFieldInfo::getProperty)
                .collect(Collectors.toSet());

        assertThat(properties).containsExactly("status");
    }

    /**
     * 验证<code>mysqlAgain</code> 指向同一实例 <code>mysql</code>，<code>jpa</code> 指向不同实例 <code>mysql</code>。
     */
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

    /**
     * 验证<code>DomainModelHelper.getModelInfo(null)</code>为空，被测调用 抛出 <code>NullPointerException.class</code>。
     */
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

        /**
         * 构造本组测试输入时复用的固定标识或名称。
         */
        private static final String TYPE = "ORDER";
        /**
         * 测试记录的框架运行或关闭状态。
         */
        private transient String runtimeState;
        /**
         * 测试替身返回、模拟或捕获的状态。
         */
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
