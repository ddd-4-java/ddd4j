package io.ddd4j.data.mybatis.plugins;

/**
 * 数据权限条件提供器 SPI。
 * <p>
 * 业务项目实现此接口，按当前登录用户 Subject 拼接数据范围条件
 * （如 {@code "dept_id IN (1,2,3)"} 或 {@code "create_by = 'admin'"}）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface DataScopeProvider {

    /**
     * 根据当前登录用户与 Mapper 方法 ID，构造追加到 WHERE 子句的 SQL 片段（不含 WHERE 关键字）。
     *
     * @param mappedStatementId MyBatis MappedStatement ID
     * @return 数据范围条件 SQL 片段；返回 null 或空字符串表示不追加
     */
    String dataScopeCondition(String mappedStatementId);
}
