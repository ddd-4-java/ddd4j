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
package io.ddd4j.quarkus.command;

import io.ddd4j.core.api.R;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通过 <code>commandBuses.isResolvable()</code>、<code>commandBuses.get()</code>、<code>result.getMsg()</code> 验证 <code>QuarkusCommandBusOverride</code> 的运行行为。
 */
@QuarkusTest
@TestProfile(QuarkusCommandBusOverrideTest.BusinessProfile.class)
class QuarkusCommandBusOverrideTest {

    /**
     * 保存测试需要比较的命令总线实例。
     */
    @Inject
    Instance<CommandBus> commandBuses;

    /**
     * 验证<code>commandBuses.isResolvable()</code>为真，<code>result.getMsg()</code> 等于 <code>"Business command bus"</code>。
     */
    @Test
    void businessCommandBusOverridesRuntimeDefault() {
        assertTrue(commandBuses.isResolvable());
        R<Void> result = commandBuses.get().execute(new SampleCommand());
        assertEquals("Business command bus", result.getMsg());
    }

    /**
     * 实现 <code>QuarkusTestProfile</code>，供测试调用 <code>getConfigOverrides()</code> 并观察结果。
     */
    public static class BusinessProfile implements QuarkusTestProfile {
        /**
         * 通过 <code>Collections.singletonMap()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>Collections.singletonMap("ddd4j.test.business-command-bus", "true")</code>
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            return Collections.singletonMap("ddd4j.test.business-command-bus", "true");
        }
    }

    /**
     * 向测试容器注册 <code>execute()</code> 等测试入口或组件。
     */
    @ApplicationScoped
    @IfBuildProperty(name = "ddd4j.test.business-command-bus", stringValue = "true")
    public static class BusinessCommandBus implements CommandBus {
        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>R.fail("Business command bus")</code>。</p>
         *
         * @param <T>     命令结果数据类型
         * @param command 待执行命令
         * @return 实际返回<code>R.fail("Business command bus")</code>
         */
        @Override
        public <T> R<T> execute(Command command) {
            return R.fail("Business command bus");
        }
    }
}
