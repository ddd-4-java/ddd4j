#!/usr/bin/env python3
"""Fail-closed checks for native Broker integration test evidence."""

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("verify-native-broker-test-reports.py")
CASES = {
    "ddd4j-mq-kafka": "KafkaMQClientRoundTripTest",
    "ddd4j-mq-rabbitmq": "RabbitMQContainerIntegrationTest",
    "ddd4j-mq-nats": "NatsJetStreamIntegrationTest",
    "ddd4j-mq-mqtt": "MqttContainerIntegrationTest",
    "ddd4j-mq-mqtt-mica": "MicaMqttContainerIntegrationTest",
    "ddd4j-mq-activemq": "ArtemisContainerIntegrationTest",
    "ddd4j-mq-pulsar": "PulsarContainerIntegrationTest",
    "ddd4j-mq-rocketmq": "RocketMQNativeIntegrationTest",
    "ddd4j-mq-redis-stream": "RedisStreamNativeCausalityIT",
    "ddd4j-mq-sqs": "SqsBrokerIntegrationTest",
    "ddd4j-mq-tdmq": "TdmqPulsarProtocolIntegrationTest",
}


class NativeBrokerReportTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for module, case in CASES.items():
            self.write_report(module, case)

    def write_report(self, module, case, tests=1, skipped=0):
        directory = self.root / "ddd4j-mq" / module / "target" / "surefire-reports"
        directory.mkdir(parents=True, exist_ok=True)
        (directory / f"TEST-io.ddd4j.{case}.xml").write_text(
            f'<testsuite name="{case}" tests="{tests}" skipped="{skipped}" '
            'errors="0" failures="0"/>\n', encoding="utf-8")

    def check(self):
        return subprocess.run([sys.executable, str(SCRIPT), "--root", str(self.root)],
                              capture_output=True, text=True, check=False)

    def test_all_required_native_tests_executed(self):
        self.assertEqual(0, self.check().returncode)

    def test_missing_report_fails(self):
        report = next((self.root / "ddd4j-mq" / "ddd4j-mq-pulsar" / "target" /
                       "surefire-reports").glob("TEST-*.xml"))
        report.unlink()
        self.assertNotEqual(0, self.check().returncode)

    def test_skipped_native_test_fails(self):
        self.write_report("ddd4j-mq-rocketmq", CASES["ddd4j-mq-rocketmq"], skipped=1)
        self.assertNotEqual(0, self.check().returncode)

    def test_missing_sqs_report_fails(self):
        report = next((self.root / "ddd4j-mq" / "ddd4j-mq-sqs" / "target" /
                       "surefire-reports").glob("TEST-*.xml"))
        report.unlink()
        self.assertNotEqual(0, self.check().returncode)

    def test_missing_kafka_report_fails(self):
        report = next((self.root / "ddd4j-mq" / "ddd4j-mq-kafka" / "target" /
                       "surefire-reports").glob("TEST-*.xml"))
        report.unlink()
        self.assertNotEqual(0, self.check().returncode)

    def test_missing_tdmq_protocol_report_fails(self):
        report = next((self.root / "ddd4j-mq" / "ddd4j-mq-tdmq" / "target" /
                       "surefire-reports").glob("TEST-*.xml"))
        report.unlink()
        self.assertNotEqual(0, self.check().returncode)

    def test_skipped_sqs_test_fails(self):
        self.write_report("ddd4j-mq-sqs", CASES["ddd4j-mq-sqs"], tests=2, skipped=2)
        self.assertNotEqual(0, self.check().returncode)

    def test_zero_executed_tests_fails(self):
        self.write_report("ddd4j-mq-redis-stream", CASES["ddd4j-mq-redis-stream"], tests=0)
        self.assertNotEqual(0, self.check().returncode)


if __name__ == "__main__":
    unittest.main()
