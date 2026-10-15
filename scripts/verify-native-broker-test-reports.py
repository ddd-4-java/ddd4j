#!/usr/bin/env python3
"""Require executed, non-skipped native Broker Testcontainers reports."""

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


REQUIRED = {
    "ddd4j-mq-rabbitmq": "RabbitMQContainerIntegrationTest",
    "ddd4j-mq-nats": "NatsJetStreamIntegrationTest",
    "ddd4j-mq-mqtt": "MqttContainerIntegrationTest",
    "ddd4j-mq-mqtt-mica": "MicaMqttContainerIntegrationTest",
    "ddd4j-mq-activemq": "ArtemisContainerIntegrationTest",
}


def verify(root: Path) -> list[str]:
    """Return every missing, skipped or failed native Broker report."""
    problems = []
    for module, case in REQUIRED.items():
        directory = root / "ddd4j-mq" / module / "target" / "surefire-reports"
        reports = list(directory.glob(f"TEST-*{case}.xml"))
        if len(reports) != 1:
            problems.append(f"{module}: expected one {case} report, found {len(reports)}")
            continue
        try:
            suite = ET.parse(reports[0]).getroot()
            if suite.tag != "testsuite":
                raise ValueError("root element is not testsuite")
            counts = {key: int(suite.attrib[key]) for key in
                      ("tests", "skipped", "errors", "failures")}
        except (ET.ParseError, OSError, KeyError, ValueError) as error:
            problems.append(f"{module}: invalid {case} report: {error}")
            continue
        if counts["tests"] <= 0 or any(counts[key] != 0 for key in
                                        ("skipped", "errors", "failures")):
            problems.append(f"{module}: {case} was not fully executed: {counts}")
        else:
            print(f"PASS: {module}/{case} ({counts['tests']} tests)")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    arguments = parser.parse_args()
    problems = verify(arguments.root)
    for problem in problems:
        print(f"FAIL: {problem}", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
