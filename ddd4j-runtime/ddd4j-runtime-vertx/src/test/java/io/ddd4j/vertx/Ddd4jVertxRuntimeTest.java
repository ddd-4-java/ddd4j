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
package io.ddd4j.vertx;

import io.ddd4j.runtime.testkit.AbstractRuntimeContractTest;
import io.ddd4j.runtime.testkit.RuntimeContract;
import io.ddd4j.runtime.testkit.RuntimeContractAdapter;
import io.ddd4j.runtime.testkit.RuntimeFixtures;
import io.vertx.core.Vertx;

class Ddd4jVertxRuntimeTest extends AbstractRuntimeContractTest {

    @Override
    protected RuntimeContract createRuntime() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        Vertx vertx = Vertx.vertx();
        Ddd4jVertxRuntime runtime = new Ddd4jVertxRuntime(vertx, fixtures.publisher(), fixtures.subjectProvider(),
                fixtures.i18nProvider(), fixtures.commandBus(), fixtures.readinessContributors());
        return new RuntimeContractAdapter(runtime::start, () -> {
            runtime.close();
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }, fixtures.services(), () -> runtime.readiness().readiness());
    }
}
