package io.ddd4j.sample.quarkus.cqrs.shared;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.Result;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuarkusDurableWiringBoundaryTest {

    @Test
    void commandTypesAreReadOnlyAndMissingNullIsAFalseLookup() {
        Set<Class<? extends Command>> types = new OrderCommandExecutor().supportedCommands();
        assertEquals(Collections.singleton(OrderWriteCommand.class), types);
        assertFalse(types.contains(null));
        assertThrows(UnsupportedOperationException.class, () -> types.remove(null));
        assertThrows(UnsupportedOperationException.class, () -> types.remove(OrderWriteCommand.class));
    }

    @Test
    void commandWithMissingResultDataKeepsDiagnostic() {
        SharedOrderResource resource = new SharedOrderResource();
        resource.commands = new CommandBus() {
            @Override
            public <R> Result<R> execute(Command command) {
                return Result.fail("test command result has no data");
            }
        };
        assertEquals("No value present",
                assertThrows(NoSuchElementException.class, () -> resource.create(null)).getMessage());
        assertEquals("No value present",
                assertThrows(NoSuchElementException.class, () -> resource.line(null)).getMessage());
        assertEquals("No value present",
                assertThrows(NoSuchElementException.class, () -> resource.pay("order-id", "key")).getMessage());
    }
}
