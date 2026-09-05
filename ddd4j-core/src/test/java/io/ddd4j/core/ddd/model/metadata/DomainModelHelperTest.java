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

/**
 * {@link DomainModelHelper} 映射隔离测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class DomainModelHelperTest {

    @AfterEach
    void tearDown() {
        DomainModelHelper.clear();
    }

    @Test
    void mappingCacheShouldBeIsolatedByPersistenceType() {
        DomainModelInfo<Order> mysqlMapping = DomainModelHelper.getModelInfo(
                Order.class, MysqlOrderPO.class, property -> "mysql_" + property);
        DomainModelInfo<Order> jpaMapping = DomainModelHelper.getModelInfo(
                Order.class, JpaOrderEntity.class, property -> "jpa_" + property);

        assertThat(mysqlMapping.getPoColumn("status")).isEqualTo("mysql_status");
        assertThat(jpaMapping.getPoColumn("status")).isEqualTo("jpa_status");
    }

    @Test
    void metadataShouldExcludeStaticTransientAndSyntheticFields() {
        DomainModelInfo<Order> mapping = DomainModelHelper.getModelInfo(Order.class);

        Set<String> properties = mapping.getFieldList().stream()
                .map(DomainFieldInfo::getProperty)
                .collect(Collectors.toSet());

        assertThat(properties).containsExactly("status");
    }

    static final class Order {

        private static final String TYPE = "ORDER";
        private transient String runtimeState;
        private String status;
    }

    static final class MysqlOrderPO {
    }

    static final class JpaOrderEntity {
    }
}
