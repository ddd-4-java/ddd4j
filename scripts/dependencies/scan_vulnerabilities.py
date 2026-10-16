#!/usr/bin/env python3
"""Scan source POMs and every managed BOM release without an NVD key."""

import argparse
import json
import subprocess
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from create_managed_bom_sbom import create_sbom
from verify_weekly_vulnerability_report import verify_report


def vulnerability_score(row):
    scores = []
    for source in (row.get('CVSS') or {}).values():
        if isinstance(source, dict):
            scores.extend(value for key, value in source.items()
                          if key.endswith('Score') and isinstance(value, (int, float)))
    if scores:
        return max(scores)
    return {'CRITICAL': 9.0, 'HIGH': 7.0, 'MEDIUM': 4.0, 'LOW': 0.1}.get(row.get('Severity'), 10.0)


def count_findings(report, threshold):
    data = json.loads(report.read_text(encoding='utf-8'))
    return sum(vulnerability_score(vulnerability) >= threshold
               for result in data['Results']
               for vulnerability in result.get('Vulnerabilities') or [])


def scan(root, output, line, threshold):
    output.mkdir(parents=True, exist_ok=True)
    manifest = output / 'managed-bom.cdx.json'
    manifest.write_text(json.dumps(create_sbom(root / 'ddd4j-dependencies/pom.xml', line)), encoding='utf-8')
    pom_report = output / 'trivy.json'
    bom_report = output / 'managed-bom-trivy.json'
    commands = (
        ['trivy', 'fs', '--scanners', 'vuln', '--format', 'json', '--exit-code', '0',
         '--output', str(pom_report), str(root)],
        ['trivy', 'sbom', '--scanners', 'vuln', '--format', 'json', '--exit-code', '0',
         '--output', str(bom_report), str(manifest)],
    )
    for command in commands:
        subprocess.run(command, check=True)
    verify_report(pom_report)
    verify_report(bom_report, manifest)
    findings = count_findings(pom_report, threshold) + count_findings(bom_report, threshold)
    print(f'Trivy findings at CVSS {threshold} or higher: {findings}')
    return int(findings > 0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--line', required=True)
    parser.add_argument('--threshold', required=True, type=float)
    args = parser.parse_args()
    try:
        return scan(args.root.resolve(), args.output.resolve(), args.line, args.threshold)
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        print(f'Vulnerability scan failed: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
