package io.ddd4j.sample.cqrs.person.application;

import io.ddd4j.sample.cqrs.person.domain.*;

import java.util.Objects;

/**
 * 人员命令服务（应用层）。
 *
 * <p>封装创建和删除人员等业务用例，协调领域模型与仓库之间的交互。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class PersonCommandService {

    private final PersonRepository repository;

    /**
     * 构造函数。
     *
     * @param repository 人员仓库（不可为 null）
     */
    public PersonCommandService(PersonRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 创建人员。
     *
     * @param command 创建人员命令
     * @return 人员 ID
     */
    public PersonId create(CreatePersonCommand command) {
        CreatePersonCommand createCommand = Objects.requireNonNull(command, "command must not be null");
        PersonId personId = new PersonId(createCommand.getPersonId());
        PersonName name = new PersonName(createCommand.getName());
        Person person = Person.create(personId, name);
        repository.save(person);
        return personId;
    }

    /**
     * 删除人员。
     *
     * @param command 删除人员命令
     * @throws IllegalArgumentException 如果人员不存在
     */
    public void delete(DeletePersonCommand command) {
        DeletePersonCommand deleteCommand = Objects.requireNonNull(command, "command must not be null");
        PersonId personId = new PersonId(deleteCommand.getPersonId());
        Person person = repository.findById(personId)
                .orElseThrow(() -> new IllegalArgumentException("person not found: " + personId.getValue()));
        person.delete();
        repository.save(person);
    }
}
