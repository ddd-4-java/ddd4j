package io.ddd4j.boot.cmpt.express.infrastructure.persistence;

import io.ddd4j.boot.cmpt.express.domain.repository.RuleDefinitionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Repository;

/**
 * 规则定义仓储实现
 * 
 * <p>基础设施层：持久化实现。
 * 这是一个接口示例，实际使用时需要根据项目的持久化方案来实现。
 * 
 * <p>实现方式：
 * <ul>
 *   <li>如果使用JPA：继承 JpaRepository&lt;RuleDefinition, Long&gt;</li>
 *   <li>如果使用MyBatis：实现 BaseMapper&lt;RuleDefinition&gt; 或使用 @Mapper 注解</li>
 *   <li>如果使用其他方案：直接实现 RuleDefinitionRepository 接口</li>
 * </ul>
 * 
 * <p>注意：此类是可选的，只有在使用Spring Data Repository时才需要。
 * 实际项目中应该根据持久化方案创建具体的实现类。
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
@Repository
@ConditionalOnClass(name = "org.springframework.data.repository.Repository")
public interface RuleDefinitionRepositoryImpl extends RuleDefinitionRepository {

    // 默认实现为空，需要根据实际持久化方案来实现
    // 示例（JPA）：
    // public interface RuleDefinitionRepositoryImpl extends RuleDefinitionRepository, JpaRepository<RuleDefinition, Long> {
    //     // JPA会自动实现基础方法
    // }
    //
    // 示例（MyBatis）：
    // @Mapper
    // public interface RuleDefinitionRepositoryImpl extends RuleDefinitionRepository {
    //     // 在XML中定义SQL
    // }
}

