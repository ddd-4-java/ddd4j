package io.ddd4j.sample.cqrs.person;

import io.ddd4j.core.domain.query.projection.DefaultProjectionService;
import io.ddd4j.core.domain.query.projection.InMemoryProjectionPositionRepository;
import io.ddd4j.core.domain.query.projection.ProjectionRunner;
import io.ddd4j.sample.cqrs.person.application.PersonCommandService;
import io.ddd4j.sample.cqrs.person.domain.CreatePersonCommand;
import io.ddd4j.sample.cqrs.person.domain.DeletePersonCommand;
import io.ddd4j.sample.cqrs.person.domain.PersonEvent;
import io.ddd4j.sample.cqrs.person.domain.PersonId;
import io.ddd4j.sample.cqrs.person.infrastructure.InMemoryPersonEventStore;
import io.ddd4j.sample.cqrs.person.infrastructure.InMemoryPersonRepository;
import io.ddd4j.sample.cqrs.person.query.PersonListView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonCqrsFlowTest {

    @Test
    void shouldProjectPersonCreatedAndDeletedEvents() {
        InMemoryPersonEventStore eventStore = new InMemoryPersonEventStore();
        InMemoryPersonRepository repository = new InMemoryPersonRepository(eventStore);
        PersonCommandService commandService = new PersonCommandService(repository);
        PersonListView view = new PersonListView();
        ProjectionRunner<PersonEvent> runner = new ProjectionRunner<>(
                new DefaultProjectionService(new InMemoryProjectionPositionRepository()),
                eventStore
        );

        PersonId personId = commandService.create(CreatePersonCommand.builder()
                .personId("p-100")
                .name("Ada")
                .build());
        runner.runOnce(view);

        assertThat(view.findById(personId.getValue()))
                .hasValueSatisfying(entry -> assertThat(entry.getName()).isEqualTo("Ada"));

        commandService.delete(DeletePersonCommand.builder()
                .personId(personId.getValue())
                .build());
        runner.runOnce(view);

        assertThat(view.findById(personId.getValue())).isEmpty();
    }
}
