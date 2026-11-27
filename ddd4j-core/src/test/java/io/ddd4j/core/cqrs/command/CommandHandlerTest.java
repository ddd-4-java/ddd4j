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
package io.ddd4j.core.cqrs.command;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CommandHandler} 注解契约测试：守护发现机制赖以成立的元注解配置——
 * 该框架无关契约现由 ddd4j-core 直接承载。
 * 运行时适配器（阶段 6 Task 6.3+）靠反射扫描读取 {@code value}，
 * 误改 Retention／Target 会让扫描静默失效（照
 * {@code io.ddd4j.core.ddd.event.EventHandlerTest} 的守护模式）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
class CommandHandlerTest {

    /** \u9a8c\u8bc1<code>target</code>\u975e\u7a7a\uff0c<code>retention</code>\u975e\u7a7a\uff0c<code>retention.value()</code> \u7b49\u4e8e <code>RetentionPolicy.RUNTIME</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>annotation</code>\u975e\u7a7a\uff0c<code>annotation.value()</code> \u7b49\u4e8e <code>CreateOrderCommand.class</code>\u3002 */
    @Test
    void valueAttributeIsReadableAtRuntime() {
        CommandHandler annotation = CreateOrderCmdExe.class.getAnnotation(CommandHandler.class);

        assertNotNull(annotation, "annotated executor class should carry @CommandHandler");
        assertEquals(CreateOrderCommand.class, annotation.value(),
                "value attribute should be reflectively readable and carry the handled command type");
    }

    /**
     * 测试用创建订单命令，用于校验 {@code @CommandHandler} 的 value 属性。
     */
    static class CreateOrderCommand implements Command {
    }

    /**
     * 处理 {@link CreateOrderCommand} 的测试命令执行器，
     * 标注 {@code @CommandHandler} 供注解契约测试反射读取。
     */
    @CommandHandler(CreateOrderCommand.class)
    static class CreateOrderCmdExe implements CommandExecutor<CreateOrderCommand> {

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Collections.&lt;Class&lt;? extends Command&gt;&gt;singleton(CreateOrderCommand.class)</code>。</p>
         *
         * @return 实际返回<code>Collections.&lt;Class&lt;? extends Command&gt;&gt;singleton(CreateOrderCommand.class)</code>
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.<Class<? extends Command>>singleton(CreateOrderCommand.class);
        }

        /**
         * 执行处理逻辑。
         *
         * @param command command 参数。
         * @return 方法执行结果。
         */
        @Override
        public Result execute(CreateOrderCommand command) {
            return Result.ok();
        }
    }
}
