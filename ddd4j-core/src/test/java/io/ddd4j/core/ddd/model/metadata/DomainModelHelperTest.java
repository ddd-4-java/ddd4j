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
