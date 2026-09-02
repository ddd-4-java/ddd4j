package io.ddd4j.core.cqrs.eventstore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventDeserializerTest {

    @AfterEach
    void resetFilter() {
        EventDeserializer.setFilter(EventDeserializer.defaultFilter());
    }

    @Nested
    class ClassNameValidation {

        @Test void legalFullyQualifiedName_returnsTrue() {
            assertTrue(EventDeserializer.isValidClassName("io.ddd4j.event.OrderCreated"));
            assertTrue(EventDeserializer.isValidClassName("com.example.$Inner$Class"));
        }

        @Test void nullOrEmpty_returnsFalse() {
            assertFalse(EventDeserializer.isValidClassName(null));
            assertFalse(EventDeserializer.isValidClassName(""));
        }

        @Test void singleSegment_returnsFalse() {
            assertFalse(EventDeserializer.isValidClassName("OrderCreated"));
        }

        @Test void segmentStartingWithDigit_returnsFalse() {
            assertFalse(EventDeserializer.isValidClassName("1io.ddd4j.Foo"));
            assertFalse(EventDeserializer.isValidClassName("io.1dd4j.Foo"));
        }

        @Test void specialCharacters_returnsFalse() {
            assertFalse(EventDeserializer.isValidClassName("io.ddd4j;exec.Foo"));
            assertFalse(EventDeserializer.isValidClassName("io.ddd4j.Foo Bar"));
            assertFalse(EventDeserializer.isValidClassName("io.ddd4j.Foo[]"));
        }
    }

    @Nested
    class DeserializeFallback {

        @Test @SuppressWarnings("unchecked")
        void invalidClassName_fallsBackToMap() {
            Object result = EventDeserializer.deserialize("{\"a\":1}", "1io.bad.class");
            assertTrue(result instanceof Map);
            assertEquals(1, ((Map<String, Object>) result).get("a"));
        }

        @Test @SuppressWarnings("unchecked")
        void missingClass_fallsBackToMap() {
            Object result = EventDeserializer.deserialize("{\"x\":\"y\"}", "io.ddd4j.NonexistentEventClass_12345");
            assertTrue(result instanceof Map);
            assertEquals("y", ((Map<String, Object>) result).get("x"));
        }
    }

    @Nested
    class ClassNameFilterSPI {

        @Test void defaultFilter_allowsAllValidFormat() {
            assertTrue(EventDeserializer.defaultFilter().allows("io.ddd4j.X"));
            assertTrue(EventDeserializer.defaultFilter().allows("java.lang.String"));
        }

        @Test @SuppressWarnings("unchecked")
        void whitelistFilter_blocksNonWhitelistedClassName() {
            final Set<String> allowedPrefixes = new HashSet<String>(Arrays.asList("io.ddd4j.", "com.example."));
            EventDeserializer.setFilter(new ClassNameFilter() {
                @Override public boolean allows(String className) {
                    for (String prefix : allowedPrefixes) if (className.startsWith(prefix)) return true;
                    return false;
                }
            });

            Object allowed = EventDeserializer.deserialize("{\"k\":\"v\"}", "io.ddd4j.SomeEvent");
            assertTrue(allowed instanceof Map);
            assertEquals("v", ((Map<String, Object>) allowed).get("k"));

            Object blocked = EventDeserializer.deserialize("{\"k\":\"v\"}", "java.lang.Runtime");
            assertTrue(blocked instanceof Map);
            assertEquals("v", ((Map<String, Object>) blocked).get("k"));
        }

        @Test void nullFilter_throwsNPE() {
            assertThrows(NullPointerException.class, () -> EventDeserializer.setFilter(null));
        }

        @Test void filterGetter_returnsCurrentFilter() {
            assertSame(EventDeserializer.defaultFilter().getClass(), EventDeserializer.filter().getClass());

            ClassNameFilter custom = new ClassNameFilter() {
                @Override public boolean allows(String name) { return name.startsWith("io.ddd4j."); }
            };
            EventDeserializer.setFilter(custom);
            assertSame(custom, EventDeserializer.filter());
        }
    }
}
