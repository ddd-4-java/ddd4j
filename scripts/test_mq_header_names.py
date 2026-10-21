"""跨 Broker 的三种消息头名称在三线保持同一公开契约。"""

from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/delivery/MQDeliveryHeaders.java"


class MQHeaderNamesTest(unittest.TestCase):
    def test_message_and_causality_header_names(self):
        pom = ET.parse(ROOT / "pom.xml").getroot()
        namespace = pom.tag.partition("}")[0] + "}" if "}" in pom.tag else ""
        version = pom.findtext("./" + namespace + "properties/" + namespace + "java.version")
        jdk = Path(subprocess.check_output(["/usr/libexec/java_home", "-v", version], text=True).strip())
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            probe = directory / "HeaderNamesProbe.java"
            probe.write_text('import io.ddd4j.mq.delivery.MQDeliveryHeaders; '
                             'public class HeaderNamesProbe { public static void main(String[] args) { '
                             'System.out.println(MQDeliveryHeaders.MESSAGE_ID + "|" + '
                             'MQDeliveryHeaders.CORRELATION_ID + "|" + MQDeliveryHeaders.CAUSATION_ID); } }\n')
            compiled = subprocess.run([str(jdk / "bin/javac"), "-d", str(directory),
                                       str(SOURCE), str(probe)], capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            result = subprocess.run([str(jdk / "bin/java"), "-cp", str(directory),
                                     "HeaderNamesProbe"], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual("ddd4j-message-id|X-Correlation-Id|X-Causation-Id", result.stdout.strip())


if __name__ == "__main__":
    unittest.main()
