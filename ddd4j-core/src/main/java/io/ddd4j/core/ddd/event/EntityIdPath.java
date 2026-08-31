package io.ddd4j.core.ddd.event;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
/** 从聚合根到事件源实体的有序标识路径。 */
public final class EntityIdPath implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<EntityId> entityIds;
    public EntityIdPath(EntityId... ids) { this(Arrays.asList(ids)); }
    public EntityIdPath(List<? extends EntityId> ids) {
        if (ids == null || ids.isEmpty() || ids.contains(null)) throw new IllegalArgumentException("Entity identifier path must contain non-null ids");
        this.entityIds = Collections.unmodifiableList(new ArrayList<EntityId>(ids));
    }
    public static EntityIdPath valueOf(String path) {
        if (StringEntityType.isBlank(path)) throw new IllegalArgumentException("Entity id path must not be blank");
        String[] segments = path.split("/", -1); List<EntityId> ids = new ArrayList<EntityId>();
        for (String segment : segments) { int index = segment.indexOf(':'); if (index <= 0 || index == segment.length() - 1) throw new IllegalArgumentException("Invalid entity id path segment: " + segment); ids.add(new StringEntityId(segment.substring(index + 1))); }
        return new EntityIdPath(ids);
    }
    @SuppressWarnings("unchecked") public <T extends EntityId> T first() { return (T) entityIds.get(0); }
    @SuppressWarnings("unchecked") public <T extends EntityId> T last() { return (T) entityIds.get(entityIds.size() - 1); }
    public EntityIdPath parent() { return entityIds.size() == 1 ? null : new EntityIdPath(entityIds.subList(0, entityIds.size() - 1)); }
    public EntityIdPath rest() { return entityIds.size() == 1 ? null : new EntityIdPath(entityIds.subList(1, entityIds.size())); }
    public int size() { return entityIds.size(); }
    public String asString() { StringBuilder result = new StringBuilder(); for (EntityId id : entityIds) { if (result.length() > 0) result.append('/'); result.append(id.asTypedString()); } return result.toString(); }
    @Override public boolean equals(Object object) { return object instanceof EntityIdPath && Objects.equals(entityIds, ((EntityIdPath) object).entityIds); }
    @Override public int hashCode() { return entityIds.hashCode(); }
    @Override public String toString() { return asString(); }
}
