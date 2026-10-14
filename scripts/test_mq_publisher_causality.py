"""领域事件转 MQ 载体时保留事件 ID 与因果链。"""

from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/event/MqDomainEventPublisher.java"
LOMBOK = Path.home() / ".m2/repository/org/projectlombok/lombok/1.18.46/lombok-1.18.46.jar"


def current_jdk():
    pom = ET.parse(ROOT / "pom.xml").getroot()
    namespace = pom.tag.partition("}")[0] + "}" if "}" in pom.tag else ""
    version = pom.findtext("./" + namespace + "properties/" + namespace + "java.version")
    return Path(subprocess.check_output(["/usr/libexec/java_home", "-v", version], text=True).strip())


class MQPublisherCausalityTest(unittest.TestCase):
    def test_carrier_copies_event_and_causality_ids(self):
        stubs = {
            "io/ddd4j/core/constant/ContextConstants.java": 'package io.ddd4j.core.constant; public final class ContextConstants { public static final String TENANT_ID = "tenant"; }',
            "io/ddd4j/core/context/ThreadContext.java": 'package io.ddd4j.core.context; public final class ThreadContext { public static String get(String key) { return null; } }',
            "io/ddd4j/core/ddd/event/EntityId.java": 'package io.ddd4j.core.ddd.event; public interface EntityId { String asString(); }',
            "io/ddd4j/core/ddd/event/DomainEvent.java": 'package io.ddd4j.core.ddd.event; public class DomainEvent<ID extends EntityId> { private EntityId eventId, correlationId, causationId; public DomainEvent(EntityId e, EntityId c, EntityId a) { eventId=e; correlationId=c; causationId=a; } public EntityId getEventId() { return eventId; } public EntityId getCorrelationId() { return correlationId; } public EntityId getCausationId() { return causationId; } public EntityId getEventType() { return () -> "OrderCreated"; } }',
            "io/ddd4j/core/ddd/event/DomainEventPublisher.java": 'package io.ddd4j.core.ddd.event; import java.util.Collection; public interface DomainEventPublisher { <ID extends EntityId> void publish(DomainEvent<ID> e); void publish(Object e); <ID extends EntityId> void publishAll(Collection<DomainEvent<ID>> events); }',
            "io/ddd4j/mq/event/MQEvent.java": 'package io.ddd4j.mq.event; public class MQEvent { public void publish() { } }',
            "io/ddd4j/mq/event/DomainEventCarrier.java": 'package io.ddd4j.mq.event; public class DomainEventCarrier extends MQEvent { public String msgId, correlationId, causationId; public DomainEventCarrier(String type, String payload) { } public void setTopic(String value) { } public void setTag(String value) { } public void setTenantId(String value) { } public void setMsgId(String value) { msgId=value; } public void setCorrelationId(String value) { correlationId=value; } public void setCausationId(String value) { causationId=value; } }',
            "io/ddd4j/mq/event/MQEventSerialization.java": 'package io.ddd4j.mq.event; import io.ddd4j.core.ddd.event.DomainEvent; public interface MQEventSerialization { String serialize(DomainEvent<?> event); }',
            "io/ddd4j/mq/serialization/JsonMQEventSerialization.java": 'package io.ddd4j.mq.serialization; import io.ddd4j.mq.event.MQEventSerialization; import io.ddd4j.core.ddd.event.DomainEvent; public class JsonMQEventSerialization implements MQEventSerialization { public String serialize(DomainEvent<?> event) { return "{}"; } }',
            "org/slf4j/Logger.java": 'package org.slf4j; public interface Logger { void warn(String format, Object... values); void info(String format, Object... values); }',
            "org/slf4j/LoggerFactory.java": 'package org.slf4j; public final class LoggerFactory { public static Logger getLogger(Class<?> type) { return new Logger() { public void warn(String f, Object... v) { } public void info(String f, Object... v) { } }; } }',
        }
        probe = '''package io.ddd4j.mq.event;
import io.ddd4j.core.ddd.event.*;
public class MQPublisherProbe {
    public static void main(String[] args) {
        EntityId id = () -> "event-1";
        EntityId correlation = () -> "root-1";
        EntityId causation = () -> "prior-1";
        DomainEventCarrier carrier = new MqDomainEventPublisher().toCarrier(new DomainEvent<EntityId>(id, correlation, causation));
        if (!"event-1".equals(carrier.msgId)) throw new AssertionError("event ID");
        if (!"root-1".equals(carrier.correlationId)) throw new AssertionError("correlation ID");
        if (!"prior-1".equals(carrier.causationId)) throw new AssertionError("causation ID");
        System.out.println("publisher causality passed");
    }
}'''
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = []
            for name, content in stubs.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                paths.append(path)
            main = root / "io/ddd4j/mq/event/MQPublisherProbe.java"
            main.write_text(probe)
            classes = root / "classes"
            classes.mkdir()
            jdk = current_jdk()
            compiled = subprocess.run([str(jdk / "bin/javac"), "-classpath", str(LOMBOK), "-d", str(classes),
                                       *map(str, paths), str(SOURCE), str(main)], capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            execution = subprocess.run([str(jdk / "bin/java"), "-classpath", str(classes),
                                        "io.ddd4j.mq.event.MQPublisherProbe"], capture_output=True, text=True)
            self.assertEqual(0, execution.returncode, execution.stderr)
            self.assertIn("publisher causality passed", execution.stdout)


if __name__ == "__main__":
    unittest.main()
