package io.ddd4j.core.cqrs.readmodel;

/** 投影位置读取、推进与重置服务。 */
public interface ProjectionService {
    void resetProjectionPosition(String streamId);
    long readProjectionPosition(String streamId);
    ProjectionPosition updateProjectionPosition(String streamId, long nextEventNumber);
    /** 返回投影状态快照（回填自 3.0.x fbada828）：默认仅含位置基线，无运行历史。 */
    default ProjectionStatus getProjectionStatus(String streamId) {
        long next = readProjectionPosition(streamId);
        return new ProjectionStatus(streamId, next, false, null, 0, null);
    }
}
