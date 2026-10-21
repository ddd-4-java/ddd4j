package io.ddd4j.data.event.store.jpa;

import io.ddd4j.core.constant.EventStoreConstants;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredEventEntityMappingTest {

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
