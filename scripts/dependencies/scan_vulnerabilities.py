#!/usr/bin/env python3
"""Scan resolved reactor dependencies and every managed BOM release without an NVD key."""

import argparse
import json
import os
import subprocess
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from create_managed_bom_sbom import create_sbom
from verify_weekly_vulnerability_report import verify_report


def vulnerability_score(row):
    scores = []
    for source in (row.get('CVSS') or {}).values():
        if isinstance(source, dict):
            scores.extend(value for key, value in source.items()
                          if key.endswith('Score') and isinstance(value, (int, float)) and value > 0)
    if scores:
        return max(scores)
    return {'CRITICAL': 9.0, 'HIGH': 7.0, 'MEDIUM': 4.0, 'LOW': 0.1}.get(row.get('Severity'), 10.0)


def count_findings(report, threshold):
    data = json.loads(report.read_text(encoding='utf-8'))
    return sum(vulnerability_score(vulnerability) >= threshold
               for result in data['Results']
               for vulnerability in result.get('Vulnerabilities') or [])


def reactor_sbom(root, output):
    """Resolve every deployable and sample module with Maven, then merge exact releases."""
    modules = []
    for pom in sorted(root.rglob('pom.xml')):
        if 'target' in pom.parts or '.git' in pom.parts:
            continue
        node = ET.parse(pom).getroot()
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        if node.findtext('m:packaging', 'jar', ns) != 'pom':
            modules.append(pom.parent)
    if not modules:
        raise ValueError('No non-POM reactor modules to scan')
    for module in modules:
        (module / 'target/weekly-deps.json').unlink(missing_ok=True)

    command = ['bash', './mvnw'] if (root / 'mvnw').is_file() else ['mvn']
    command += ['-B', '-ntp', '-DskipTests', '-Denforcer.skip=true',
                '-Dquarkus.build.skip=true', '-Dcyclonedx.skipNotDeployed=false',
                '-DincludeTestScope=true', '-DincludeProvidedScope=true',
                'org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeBom',
                '-DoutputFormat=json', '-DoutputName=weekly-deps']
    env = os.environ.copy()
    if env.get('BUILD_JAVA_HOME'):
        env['JAVA_HOME'] = env['BUILD_JAVA_HOME']
        env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env.get('PATH', '')
    subprocess.run(command, cwd=root, env=env, check=True)

    missing = [str(module.relative_to(root)) for module in modules
               if not (module / 'target/weekly-deps.json').is_file()]
    if missing:
        raise ValueError('CycloneDX omitted reactor modules: ' + ', '.join(missing[:5]))

    components = {}
    dependencies = {}
    for module in modules:
        data = json.loads((module / 'target/weekly-deps.json').read_text(encoding='utf-8'))
        if data.get('bomFormat') != 'CycloneDX' or not isinstance(data.get('components'), list):
            raise ValueError('Invalid CycloneDX module SBOM: ' + str(module.relative_to(root)))
        for component in data['components']:
            identity = (component.get('group'), component.get('name'), component.get('version'),
                        component.get('purl'))
            if not all(identity):
                raise ValueError('Unversioned CycloneDX component: ' + str(module.relative_to(root)))
            components[identity] = component
        for dependency in data.get('dependencies') or []:
            if dependency.get('ref'):
                dependencies[dependency['ref']] = dependency
    if not components:
        raise ValueError('CycloneDX reactor SBOM has no resolved components')
    manifest = output / 'reactor-bom.cdx.json'
    manifest.write_text(json.dumps({
        'bomFormat': 'CycloneDX', 'specVersion': '1.6', 'version': 1,
        'components': [components[key] for key in sorted(components)],
        'dependencies': [dependencies[key] for key in sorted(dependencies)],
    }), encoding='utf-8')
    print(f'CycloneDX resolved {len(modules)} modules and {len(components)} component releases')
    return manifest


def scan(root, output, line, threshold):
    output.mkdir(parents=True, exist_ok=True)
    manifest = output / 'managed-bom.cdx.json'
    manifest.write_text(json.dumps(create_sbom(root / 'ddd4j-dependencies/pom.xml', line)), encoding='utf-8')
    reactor_manifest = reactor_sbom(root, output)
    reactor_report = output / 'trivy.json'
    bom_report = output / 'managed-bom-trivy.json'
    commands = (
        ['trivy', 'sbom', '--scanners', 'vuln', '--format', 'json', '--exit-code', '0',
         '--output', str(reactor_report), str(reactor_manifest)],
        ['trivy', 'sbom', '--scanners', 'vuln', '--format', 'json', '--exit-code', '0',
         '--output', str(bom_report), str(manifest)],
    )
    for command in commands:
        subprocess.run(command, check=True)
    verify_report(reactor_report, reactor_manifest)
    verify_report(bom_report, manifest)
    findings = count_findings(reactor_report, threshold) + count_findings(bom_report, threshold)
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
