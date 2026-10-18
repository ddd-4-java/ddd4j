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
package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import javax.enterprise.inject.Instance;
import io.quarkus.scheduler.Scheduled;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 三线共同的 Quarkus 投影拉取契约。 */
class QuarkusJpaProjectionServiceContractTest {

    @Test
    void usesThirtySecondDefaultCron() throws Exception {
        Scheduled scheduled = QuarkusJpaProjectionService.class.getDeclaredMethod("scheduled")
                .getAnnotation(Scheduled.class);
        String expression = scheduled.cron();
        String defaultCron;
        if (expression.startsWith("${")) {
            defaultCron = expression.substring(expression.indexOf(':') + 1, expression.length() - 1);
        } else {
            String property = expression.substring(1, expression.length() - 1);
            try (InputStream stream = getClass().getResourceAsStream("/application.properties")) {
                assertNotNull(stream);
                Properties properties = new Properties();
                properties.load(stream);
                defaultCron = properties.getProperty(property);
            }
        }
        assertEquals("*/30 * * * * ?", defaultCron);
    }

    @Test
    void skipsMissingOrEmptyRepository() {
        RecordingProjectionService service = new RecordingProjectionService();
        Instance<ProjectionPositionRepository> instance = repositoryInstance();
        service.projectionPositionRepository = instance;
        when(instance.isUnsatisfied()).thenReturn(true);

        service.runOnce();

        verify(instance, never()).get();
        assertEquals(Collections.emptyList(), service.streams);

        ProjectionPositionRepository repository = mock(ProjectionPositionRepository.class);
        when(instance.isUnsatisfied()).thenReturn(false);
        when(instance.get()).thenReturn(repository);
        when(repository.findAll()).thenReturn(Collections.emptyList());

        service.runOnce();

        assertEquals(Collections.emptyList(), service.streams);
    }

    @Test
    void failureInOneStreamDoesNotSkipTheNext() {
        RecordingProjectionService service = new RecordingProjectionService();
        service.failOn = "failed";
        Instance<ProjectionPositionRepository> instance = repositoryInstance();
        ProjectionPositionRepository repository = mock(ProjectionPositionRepository.class);
        ProjectionPosition first = mock(ProjectionPosition.class);
        ProjectionPosition second = mock(ProjectionPosition.class);
        when(instance.get()).thenReturn(repository);
        when(repository.findAll()).thenReturn(Arrays.asList(first, second));
        when(first.getStreamId()).thenReturn("failed");
        when(second.getStreamId()).thenReturn("next");
        service.projectionPositionRepository = instance;

        service.runOnce();

        assertEquals(Arrays.asList("failed", "next"), service.streams);
    }

    @SuppressWarnings("unchecked")
    private Instance<ProjectionPositionRepository> repositoryInstance() {
        return mock(Instance.class);
    }

    private static final class RecordingProjectionService extends QuarkusJpaProjectionService {

        private final List<String> streams = new ArrayList<>();
        private String failOn;

        @Override
        protected void pullAndApply(String streamId) {
            streams.add(streamId);
            if (streamId.equals(failOn)) {
                throw new IllegalStateException("simulated projection failure");
            }
        }
    }
}
