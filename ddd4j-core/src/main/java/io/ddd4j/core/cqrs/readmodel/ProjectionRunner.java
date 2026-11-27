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

    /** \u5e94\u7528\u6295\u5f71\u4e8b\u4ef6\u53ca\u7ef4\u62a4\u68c0\u67e5\u70b9\u7684\u5171\u4eab\u670d\u52a1\u3002 */
    private final ProjectionService projectionService;

    /** \u6309\u4f4d\u70b9\u5206\u5757\u8bfb\u53d6\u4e8b\u4ef6\u7684\u6295\u5f71\u7ec4\u4ef6\u3002 */
    private final EventChunkReader<E> chunkReader;

    /**
     * \u6784\u9020\u6295\u5f71\u8fd0\u884c\u5668\uff08\u65e0\u6307\u6807\u91c7\u96c6\uff09\u3002
     * @param projectionService \u6295\u5f71\u4f4d\u7f6e\u670d\u52a1
     * @param chunkReader \u4e8b\u4ef6\u5757\u8bfb\u53d6\u5668
     */
    public ProjectionRunner(ProjectionService projectionService, EventChunkReader<E> chunkReader) {
        this.projectionService = Objects.requireNonNull(projectionService, "projectionService must not be null");
        this.chunkReader = Objects.requireNonNull(chunkReader, "chunkReader must not be null");
    }

    /**
     * \u8fd0\u884c\u5355\u4e2a\u89c6\u56fe\u7684\u4e00\u6b21\u589e\u91cf\u6295\u5f71\u3002
     * @param view \u6295\u5f71\u89c6\u56fe
     * @return \u672c\u6b21\u8bfb\u53d6\u51fa\u7684\u4e8b\u4ef6\u5757
     *
     * @throws IllegalStateException \u540c\u4e00\u8fd0\u884c\u5668\u4e2d\u8be5\u6d41\u5df2\u6709\u6267\u884c\uff0c\u8c03\u7528\u65b9\u53ef\u7a0d\u540e\u91cd\u8bd5
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

    /**
     * \u5728\u8fd0\u884c\u524d\u68c0\u67e5\u89c6\u56fe\u53ca\u5176\u4e8b\u4ef6\u6d41\u540d\u79f0\u3002
     * @param view \u8bf7\u6c42\u6267\u884c\u7684\u6295\u5f71\u89c6\u56fe
     * @return \u6821\u9a8c\u901a\u8fc7\u7684\u6295\u5f71\u89c6\u56fe
     */
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
