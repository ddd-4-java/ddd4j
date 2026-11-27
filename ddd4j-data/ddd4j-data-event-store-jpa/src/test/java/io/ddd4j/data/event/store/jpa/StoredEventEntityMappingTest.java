package io.ddd4j.data.event.store.jpa;

import io.ddd4j.core.constant.EventStoreConstants;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/** \u901a\u8fc7 <code>StoredEventEntity.class.getAnnotation()</code>\u3001<code>entity.name()</code>\u3001<code>StoredEventEntity.class.getSimpleName()</code> \u9a8c\u8bc1 <code>StoredEventEntityMapping</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class StoredEventEntityMappingTest {

    /**
     * \u9a8c\u8bc1<code>entity</code>\u975e\u7a7a\uff0c<code>entity.name().isEmpty() ? StoredEventEntity.class.getSimpleName() : entity.name()</code> \u7b49\u4e8e <code>"StoredEventEntity"</code>\uff0c<code>constraint.columnNames()[0]</code> \u7b49\u4e8e <code>EventStoreConstants.COLUMN_POSITION</code>\u3002
     * @throws NoSuchFieldException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u5b57\u6bb5\u5931\u8d25
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
