package io.ddd4j.core.cqrs.readmodel;

/** 投影位置读取、推进与重置服务。 */
public interface ProjectionService {
    void resetProjectionPosition(String streamId);
    long readProjectionPosition(String streamId);
    ProjectionPosition updateProjectionPosition(String streamId, long nextEventNumber);
}
