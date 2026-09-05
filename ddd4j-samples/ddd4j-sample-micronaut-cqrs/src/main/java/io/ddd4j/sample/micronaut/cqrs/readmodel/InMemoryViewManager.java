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
package io.ddd4j.sample.micronaut.cqrs.readmodel;

import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionService;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 core {@link ProjectionRunner} 的内存视图管理器。
 *
 * <p>实现 core {@link ViewManager} 接口，
 * 使用 {@link InMemoryProjectionPositionRepository} 跟踪投影位置，
 * 通过 {@link ProjectionRunner} 执行增量投影。
 */
public class InMemoryViewManager implements ViewManager {

    private final ProjectionRunner<Object> runner;
    private final List<ProjectionView<Object>> views = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public InMemoryViewManager(InMemoryEventChunkReader chunkReader) {
        Objects.requireNonNull(chunkReader, "chunkReader must not be null");
        InMemoryProjectionPositionRepository positionRepo = new InMemoryProjectionPositionRepository();
        DefaultProjectionService projectionService = new DefaultProjectionService(positionRepo);
        this.runner = new ProjectionRunner<>(projectionService, chunkReader);
    }

    public void register(ProjectionView<Object> view) {
        views.add(view);
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(this::triggerOnce, 5, 5, TimeUnit.SECONDS);
        }
    }

    @Override
    public void triggerOnce() {
        runner.runAll(views);
    }

    @Override
    public void stop() {
        running.set(false);
        scheduler.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
