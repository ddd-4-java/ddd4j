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

    /** \u8ba2\u5355\u9886\u57df\u6a21\u578b\u6d4b\u8bd5\u5939\u5177\uff0c\u5305\u542b\u9759\u6001\u3001\u77ac\u6001\u4e0e\u666e\u901a\u5b57\u6bb5\u4ee5\u9a8c\u8bc1\u5143\u6570\u636e\u8fc7\u6ee4\u3002 */
    static final class Order {

        /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u6807\u8bc6\u6216\u540d\u79f0\u3002 */
        private static final String TYPE = "ORDER";
        /** \u6d4b\u8bd5\u8bb0\u5f55\u7684\u6846\u67b6\u8fd0\u884c\u6216\u5173\u95ed\u72b6\u6001\u3002 */
        private transient String runtimeState;
        /** \u6d4b\u8bd5\u66ff\u8eab\u8fd4\u56de\u3001\u6a21\u62df\u6216\u6355\u83b7\u7684\u72b6\u6001\u3002 */
        private String status;
    }

    /** \u5173\u7cfb\u578b\u6301\u4e45\u5316\u5bf9\u8c61\u6d4b\u8bd5\u5939\u5177\uff0c\u7528\u4e8e\u533a\u5206\u6309\u6301\u4e45\u5316\u7c7b\u578b\u9694\u79bb\u7684\u6620\u5c04\u7f13\u5b58\u3002 */
    static final class MysqlOrderPO {
    }

    /** \u5bf9\u8c61\u5173\u7cfb\u6620\u5c04\u5b9e\u4f53\u6d4b\u8bd5\u5939\u5177\uff0c\u7528\u4e8e\u533a\u5206\u6309\u6301\u4e45\u5316\u7c7b\u578b\u9694\u79bb\u7684\u6620\u5c04\u7f13\u5b58\u3002 */
    static final class JpaOrderEntity {
    }
}
