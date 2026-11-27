package io.ddd4j.data.event.store.jpa;

import io.ddd4j.core.constant.EventStoreConstants;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>StoredEventEntity.class.getAnnotation()</code>、<code>entity.name()</code>、<code>StoredEventEntity.class.getSimpleName()</code> 验证 <code>StoredEventEntityMapping</code> 的运行行为。
 */
class StoredEventEntityMappingTest {

    /**
     * 验证<code>entity</code>非空，<code>entity.name().isEmpty() ? StoredEventEntity.class.getSimpleName() : entity.name()</code> 等于 <code>"StoredEventEntity"</code>，<code>constraint.columnNames()[0]</code> 等于 <code>EventStoreConstants.COLUMN_POSITION</code>。
     *
     * @throws NoSuchFieldException 反射定位被测字段失败
     */
    @Test
    void mapsSharedEventStoreIdentityAndPositionConstraint() throws NoSuchFieldException {
        Entity entity = StoredEventEntity.class.getAnnotation(Entity.class);
        assertNotNull(entity);
        assertEquals("StoredEventEntity", entity.name().isEmpty()
                ? StoredEventEntity.class.getSimpleName() : entity.name());

        for (String fieldName : new String[]{"aggregateType", "aggregateId", "version"}) {
            Field field = StoredEventEntity.class.getDeclaredField(fieldName);
            assertTrue(field.isAnnotationPresent(Id.class), fieldName);
        }

        Table table = StoredEventEntity.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals(EventStoreConstants.TABLE_NAME, table.name());
        assertEquals(1, table.uniqueConstraints().length);
        UniqueConstraint constraint = table.uniqueConstraints()[0];
        assertEquals("uk_event_store_position", constraint.name());
        assertEquals(1, constraint.columnNames().length);
        assertEquals(EventStoreConstants.COLUMN_POSITION, constraint.columnNames()[0]);
    }
}
