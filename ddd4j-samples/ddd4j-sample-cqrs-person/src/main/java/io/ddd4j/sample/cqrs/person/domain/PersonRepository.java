package io.ddd4j.sample.cqrs.person.domain;

import java.util.Optional;

/**
 * 人员仓库接口。
 *
 * <p>定义人员聚合的持久化契约，包括保存和按 ID 查询。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface PersonRepository {

    /**
     * 保存人员聚合（含未提交的事件）。
     *
     * @param person 人员聚合
     */
    void save(Person person);

    /**
     * 根据 ID 查询人员。
     *
     * @param id 人员 ID
     * @return 查询结果
     */
    Optional<Person> findById(PersonId id);
}
