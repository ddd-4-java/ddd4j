package io.ddd4j.data.cqrs;

import java.util.Collections;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link CommandHandler} 注解契约测试：守护发现机制赖以成立的元注解配置——
 * 运行时适配器（阶段 6 Task 6.3+）靠反射扫描读取 {@code value}，
 * 误改 Retention／Target 会让扫描静默失效（照
 * {@code io.ddd4j.core.ddd.event.EventHandlerTest} 的守护模式）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class CommandHandlerTest {

    @Test
    void annotationDeclarationIsTypeTargetedAndRuntimeRetained() {
        Target target = CommandHandler.class.getAnnotation(Target.class);
        assertNotNull(target, "@CommandHandler must declare @Target");
        assertArrayEquals(new ElementType[]{ElementType.TYPE}, target.value(),
                "@CommandHandler must target types only (CommandExecutor implementation classes)");

        Retention retention = CommandHandler.class.getAnnotation(Retention.class);
        assertNotNull(retention, "@CommandHandler must declare @Retention");
        assertEquals(RetentionPolicy.RUNTIME, retention.value(),
                "@CommandHandler must be visible at runtime for reflective scanning by adapters");
    }

    @Test
    void valueAttributeIsReadableAtRuntime() {
        CommandHandler annotation = CreateOrderCmdExe.class.getAnnotation(CommandHandler.class);

        assertNotNull(annotation, "annotated executor class should carry @CommandHandler");
        assertEquals(CreateOrderCommand.class, annotation.value(),
                "value attribute should be reflectively readable and carry the handled command type");
    }

    static class CreateOrderCommand implements Command {
    }

    @CommandHandler(CreateOrderCommand.class)
    static class CreateOrderCmdExe implements CommandExecutor<CreateOrderCommand> {

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.singleton(CreateOrderCommand.class);
        }

        @Override
        public Result execute(CreateOrderCommand command) {
            return Result.ok();
        }
    }
}
