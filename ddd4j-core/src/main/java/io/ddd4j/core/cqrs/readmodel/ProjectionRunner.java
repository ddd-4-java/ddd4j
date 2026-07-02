package io.ddd4j.core.cqrs.readmodel;

import io.ddd4j.kit.lang.CollKit;
import io.ddd4j.kit.lang.StrKit;

import java.util.Collection;
import java.util.Objects;

/**
 * 增量投影运行器。
 *
 * <p>封装通用投影流程：读取当前位置、拉取事件块、执行业务投影、推进位置。
 * 框架适配层只需要负责调度、事务和 Bean 装配。
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class ProjectionRunner<E> {

    private final ProjectionService projectionService;

    private final EventChunkReader<E> chunkReader;

    public ProjectionRunner(ProjectionService projectionService, EventChunkReader<E> chunkReader) {
        this.projectionService = Objects.requireNonNull(projectionService, "projectionService must not be null");
        this.chunkReader = Objects.requireNonNull(chunkReader, "chunkReader must not be null");
    }

    /**
     * 运行单个视图的一次增量投影。
     *
     * @param view 投影视图
     * @return 本次读取出的事件块
     */
    public EventChunk<E> runOnce(ProjectionView<E> view) {
        ProjectionView<E> projectionView = validateView(view);
        long nextEventNumber = projectionService.readProjectionPosition(projectionView.getStreamId());
        EventChunk<E> chunk = chunkReader.read(
                projectionView.getStreamId(),
                nextEventNumber,
                projectionView.getChunkSize(),
                projectionView.getEventTypes()
        );
        EventChunk<E> safeChunk = Objects.requireNonNull(chunk, "chunkReader must not return null");
        if (safeChunk.hasEvents()) {
            projectionView.handleEvents(safeChunk.getEvents());
        }
        if (safeChunk.getNextEventNumber() > nextEventNumber) {
            projectionService.updateProjectionPosition(projectionView.getStreamId(), safeChunk.getNextEventNumber());
        }
        return safeChunk;
    }

    /**
     * 运行多个视图的一次增量投影。
     *
     * @param views 投影视图集合
     */
    public void runAll(Collection<? extends ProjectionView<E>> views) {
        if (CollKit.isEmpty(views)) {
            return;
        }
        for (ProjectionView<E> view : views) {
            runOnce(view);
        }
    }

    private ProjectionView<E> validateView(ProjectionView<E> view) {
        ProjectionView<E> projectionView = Objects.requireNonNull(view, "view must not be null");
        if (StrKit.isBlank(projectionView.getName())) {
            throw new IllegalArgumentException("view name must not be blank");
        }
        if (StrKit.isBlank(projectionView.getStreamId())) {
            throw new IllegalArgumentException("view streamId must not be blank");
        }
        if (projectionView.getChunkSize() <= 0) {
            throw new IllegalArgumentException("view chunkSize must be positive");
        }
        return projectionView;
    }
}
