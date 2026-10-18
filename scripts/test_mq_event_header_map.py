"""MQEvent 的 broker header 映射在三条产品线保持一致。"""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/event/MQEvent.java"
HEADERS = ROOT / "ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/delivery/MQDeliveryHeaders.java"
LOMBOK = Path.home() / ".m2/repository/org/projectlombok/lombok/1.18.46/lombok-1.18.46.jar"
JACKSON = Path.home() / ".m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.18.11/jackson-annotations-2.18.11.jar"


def current_jdk():
    configured = os.environ.get("JAVA_HOME")
    if configured:
        return Path(configured)
    pom = ET.parse(ROOT / "pom.xml").getroot()
    namespace = pom.tag.partition("}")[0] + "}" if "}" in pom.tag else ""
    version = pom.findtext("./" + namespace + "properties/" + namespace + "java.version")
    return Path(subprocess.check_output(["/usr/libexec/java_home", "-v", version], text=True).strip())


class MQEventHeaderMapTest(unittest.TestCase):
    def test_header_map_contains_only_nonempty_values_and_is_immutable(self):
        jdk = current_jdk()
        stubs = {
            "io/ddd4j/core/constant/ContextConstants.java": 'package io.ddd4j.core.constant; public final class ContextConstants { public static final String TENANT_ID = "tenant"; }',
            "io/ddd4j/core/context/BaseContext.java": 'package io.ddd4j.core.context; public final class BaseContext { public static <T> T get(String key) { return null; } }',
            "io/ddd4j/core/context/ThreadContext.java": 'package io.ddd4j.core.context; public final class ThreadContext { public static String get(String key) { return null; } }',
            "io/ddd4j/kit/lang/StrKit.java": 'package io.ddd4j.kit.lang; public final class StrKit { public static boolean isEmpty(String value) { return value == null || value.isEmpty(); } public static boolean isNotEmpty(String value) { return !isEmpty(value); } }',
            "io/ddd4j/mq/MQProperties.java": 'package io.ddd4j.mq; public class MQProperties { public String broker; public String getBroker() { return broker; } public String getDefaultTopic() { return "DEFAULT"; } }',
            "io/ddd4j/mq/MQClient.java": 'package io.ddd4j.mq; import io.ddd4j.mq.event.MQEvent; import java.util.function.Consumer; public interface MQClient { String impl(); Consumer<MQEvent> initProducer(MQProperties properties); }',
            "org/slf4j/Logger.java": 'package org.slf4j; public interface Logger { void warn(String format, Object... values); }',
            "org/slf4j/LoggerFactory.java": 'package org.slf4j; public final class LoggerFactory { public static Logger getLogger(Class<?> type) { return (format, values) -> { }; } }',
        }
        probe = '''import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import java.util.Map;
public class MQEventHeaderProbe {
    public static void main(String[] args) {
        MQEvent event = new MQEvent();
        if (!event.headerMap().isEmpty()) throw new AssertionError("empty");
        event.setMsgId("m-1");
        event.setCorrelationId("c-1");
        event.setCausationId("a-1");
        Map<String, String> headers = event.headerMap();
        if (headers.size() != 3) throw new AssertionError(headers);
        if (!"m-1".equals(headers.get(MQDeliveryHeaders.MESSAGE_ID))) throw new AssertionError(headers);
        if (!"c-1".equals(headers.get(MQDeliveryHeaders.CORRELATION_ID))) throw new AssertionError(headers);
        if (!"a-1".equals(headers.get(MQDeliveryHeaders.CAUSATION_ID))) throw new AssertionError(headers);
        try { headers.put("other", "value"); throw new AssertionError("mutable"); }
        catch (UnsupportedOperationException expected) { }
        event.setCorrelationId("");
        if (event.headerMap().containsKey(MQDeliveryHeaders.CORRELATION_ID)) throw new AssertionError("blank correlation");
        System.out.println("MQEvent headers passed");
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
            main = root / "MQEventHeaderProbe.java"
            main.write_text(probe)
            classes = root / "classes"
            classes.mkdir()
            classpath = str(LOMBOK) + ":" + str(JACKSON)
            compiled = subprocess.run([str(jdk / "bin/javac"), "-classpath", classpath, "-d", str(classes),
                                       *map(str, paths), str(HEADERS), str(SOURCE), str(main)],
                                      capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            execution = subprocess.run([str(jdk / "bin/java"), "-classpath", str(classes), "MQEventHeaderProbe"],
                                       capture_output=True, text=True)
            self.assertEqual(0, execution.returncode, execution.stderr)
            self.assertIn("MQEvent headers passed", execution.stdout)


if __name__ == "__main__":
    unittest.main()
