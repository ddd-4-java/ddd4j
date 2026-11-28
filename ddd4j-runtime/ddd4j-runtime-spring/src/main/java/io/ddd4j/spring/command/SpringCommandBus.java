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
package io.ddd4j.spring.command;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandRegistry;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.api.R;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Collection;
import java.util.Set;

/**
 * Spring 命令总线。
 * <p>
 * 基于框架中立的 ddd4j 命令 SPI 实现，在容器刷新后扫描所有 {@link CommandExecutor} Bean
 * 并构建命令类型到执行器的路由表。与 Quarkus {@code QuarkusCommandBus} 行为对齐。
 *
 * <h2>发现机制</h2>
 * <ul>
 *   <li>扫描容器中所有实现 {@link CommandExecutor} 接口的 Bean</li>
 *   <li>兼容 {@code @io.ddd4j.spring.annotation.CommandExecutor} 注解标记的 Bean
 *       （该注解融合了 {@code @Component}，被扫描的 Bean 同时实现接口即可自动注册）</li>
 * </ul>
 *
 * <h3>事务策略</h3>
 * <p>
 * {@link #execute(Command)} 采用<b>方法级</b> {@link Transactional @Transactional}：
 * Spring 事务切面的属性查找先查被调方法自身、再查其声明类——继承自父类的方法其声明类在父类上，
 * 类级注解对子类继承方法不生效（历史缺陷：类级注解在集成方带事务管理器时静默分发非事务）。
 * 方法级注解落在本类自己的方法上，{@code PlatformTransactionManager} 存在时由 Spring 代理真实生效。
 * 因此本类<b>不可 {@code final}</b>（CGLIB 代理需子类化）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see CommandExecutor
 * @see io.ddd4j.spring.annotation.CommandExecutor
 * @since 3.0.0
 */
@Component
@SuppressWarnings({"rawtypes", "unchecked"})
public class SpringCommandBus implements CommandBus, SmartInitializingSingleton {

    /**
     * Spring 应用上下文
     */
    private final ApplicationContext applicationContext;

    /** 显式注册表入口在构造期完成发现，容器刷新时保留调用方提供的路由。 */
    private final boolean discoverOnRefresh;
    // 只在完整发现成功后发布快照；路由和执行完全归共享内核。
    /**
     * 已从 Spring 容器发现的命令执行器及路由类型快照。
     */
    private volatile DiscoveredCommands discovered;

    /**
     * \u521b\u5efa\u901a\u8fc7 Spring \u4e0a\u4e0b\u6587\u67e5\u627e\u6267\u884c\u5668\u7684\u547d\u4ee4\u603b\u7ebf\u3002
     * @param applicationContext Spring \u5e94\u7528\u4e0a\u4e0b\u6587
     */
    @Autowired
    public SpringCommandBus(ApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext must not be null");
        this.discoverOnRefresh = true;
    }

    /**
     * 将容器执行器加入显式注册表，并纳入调用方预注册的路由。
     *
     * @param context 提供执行器的应用上下文
     * @param registry 调用方维护的命令注册表
     */
    public SpringCommandBus(ApplicationContext context, CommandRegistry registry) {
        this.applicationContext = Objects.requireNonNull(context, "context must not be null");
        this.discoverOnRefresh = false;
        Objects.requireNonNull(registry, "registry must not be null");
        for (CommandExecutor<?> executor : context.getBeansOfType(CommandExecutor.class).values()) {
            registry.register(executor);
        }
        discover(registry.executors());
    }

    /**
     * 容器刷新后扫描所有 {@link CommandExecutor} Bean 并注册到路由表。
     * <p>
     * 使用 {@link SmartInitializingSingleton} 回调，确保所有单例 Bean 已初始化完毕后再扫描，
     * 避免循环依赖和遗漏延迟初始化的 Bean。
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (discoverOnRefresh) {
            discover(applicationContext.getBeansOfType(CommandExecutor.class).values());
        }
    }

    /**
     * 仅在所有执行器均通过路由冲突检查后发布完整快照。
     *
     * @param executors 当前装配入口收集的执行器
     */
    private void discover(Collection<? extends CommandExecutor> executors) {
        List<CommandExecutor<?>> snapshots = new ArrayList<>();
        Set<Class<? extends Command>> types = new HashSet<>();
        for (CommandExecutor executor : executors) {
            Set<Class<? extends Command>> supported = Set.copyOf(executor.supportedCommands());
            types.addAll(supported);
            snapshots.add(new CommandExecutor<Command>() {
                @Override
                public Set<Class<? extends Command>> supportedCommands() {
                    return supported;
                }

                @Override
                public R<?> execute(Command command) {
                    return executor.execute(command);
                }
            });
        }
        discovered = new DiscoveredCommands(new DefaultCommandBus(snapshots), types);
    }

    /**
     * 执行命令并返回结果。
     * <p>
     * 必须<b>方法级</b> {@code @Transactional}：Spring 事务切面对继承方法只查方法自身与其声明类，
     * 类级注解对本类的继承方法不生效（见类 javadoc「事务策略」）。
     *
     * @param command 命令对象，非空
     * @param <T>     结果载荷类型
     * @return 执行结果
     * @throws IllegalArgumentException command 为 null
     * @throws IllegalStateException    未找到对应执行器
     */
    @Override
    @Transactional
    public <T> R<T> execute(Command command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("Command cannot be null");
        }

        DiscoveredCommands snapshot = discovered;
        if (Objects.isNull(snapshot) || !snapshot.types.contains(command.getClass())) {
            throw new IllegalStateException(
                    "No executor found for command: " + command.getClass().getName()
                            + ". Ensure the executor bean implements CommandExecutor interface "
                            + "and its supportedCommands() includes this command type.");
        }
        return snapshot.delegate.execute(command);
    }
    /**
     * 保存已装配的 Spring 命令总线及其支持命令类型的发现结果。
     */
    private static final class DiscoveredCommands {
        /**
         * 使用已发现执行器完成命令分发的共享总线。
         */
        private final DefaultCommandBus delegate;
        /**
         * 当前从 Spring 容器发现的受支持命令类型集合。
         */
        private final Set<Class<? extends Command>> types;

        /**
         * 将容器已发现的命令类型与同一轮装配的总线一起保存。
         *
         * @param delegate 当前发现轮次组装的共享命令总线
         * @param types    该总线支持的命令类型
         */
        private DiscoveredCommands(DefaultCommandBus delegate, Set<Class<? extends Command>> types) {
            this.delegate = delegate;
            this.types = Set.copyOf(types);
        }
    }
}
