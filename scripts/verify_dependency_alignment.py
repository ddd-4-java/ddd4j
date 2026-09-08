#!/usr/bin/env python3
"""验证三线显式组件能力覆盖和实际发布 JAR 的基础字节码。

输入必须为本次 Maven 生成的 effective POM；不模拟 Maven BOM 优先级。
缺少 JAR 的结果明确记为未验证；--strict-artifacts 可将其作为失败。
"""
import argparse
import csv
import json
import struct
import sys
import zipfile
from pathlib import Path


def dependencies(path):
    import xml.etree.ElementTree as ET
    root = ET.parse(path).getroot()
    ns = {'m': root.tag.split('}')[0][1:]}
    result = {}
    for dep in root.findall('m:dependencyManagement/m:dependencies/m:dependency', ns):
        row = {node.tag.split('}')[-1]: node.text for node in dep if len(node) == 0}
        key = (row['groupId'], row['artifactId'], row.get('type', 'jar'), row.get('classifier', ''))
        if key in result:
            raise ValueError(f'{path}: duplicate managed coordinate {key}')
        result[key] = row
    return result


def bytecode(path):
    counts = {}
    with zipfile.ZipFile(path) as jar:
        for name in jar.namelist():
            if not name.endswith('.class') or name.startswith('META-INF/versions/') or name.endswith('module-info.class'):
                continue
            with jar.open(name) as stream:
                header = stream.read(8)
            if len(header) != 8 or header[:4] != b'\xca\xfe\xba\xbe':
                raise ValueError(f'invalid class: {path}!{name}')
            jdk = struct.unpack('>H', header[6:8])[0] - 44
            counts[jdk] = counts.get(jdk, 0) + 1
    return counts


def profile_dependencies(path):
    import xml.etree.ElementTree as ET
    root = ET.parse(path).getroot()
    ns = {'m': root.tag.split('}')[0][1:]}
    result = {}
    for profile in root.findall('m:profiles/m:profile', ns):
        name = profile.findtext('m:id', namespaces=ns)
        for dep in profile.findall('m:dependencyManagement/m:dependencies/m:dependency', ns):
            row = {node.tag.split('}')[-1]: node.text for node in dep if len(node) == 0}
            key = (row['groupId'], row['artifactId'], row.get('type', 'jar'), row.get('classifier', ''))
            result[key] = name
    return result


def verify(roots, effective_paths, policy, repository, extra_cache, strict):
    direct = [dependencies(root / 'ddd4j-dependencies/pom.xml') for root in roots]
    profiles = [profile_dependencies(root / 'ddd4j-dependencies/pom.xml') for root in roots]
    effective = [dependencies(path) for path in effective_paths]
    errors, unknown, rows = [], [], []
    keys = set().union(*(set(items) for items in direct + profiles))
    variants = policy.get('baseline_variants', {})
    if variants:
        for identity in variants:
            original = tuple(identity.split(':', 3))
            if original[3] or not any(key[:2] == original[:2] and not key[3] for key in keys):
                keys.add(original)
    else:
        for coordinate in policy.get('baseline_components', {}):
            group, artifact = coordinate.split(':')
            if not any(key[:2] == (group, artifact) and not key[3] for key in keys):
                keys.add((group, artifact, 'jar', ''))
    for key in sorted(keys):
        coordinate = ':'.join(key[:2])
        display_coordinate = coordinate + (':' + key[2] + ':' + key[3] if key[3] else '')
        values = [items.get(key) for items in effective]
        # imported BOMs disappear from effective dependencyManagement; coverage is
        # checked through their actual component entries, not the BOM coordinate.
        if any(items.get(key, {}).get('scope') == 'import' for items in direct):
            continue
        origins = [i for i in range(3) if key in direct[i] or key in profiles[i]]
        baseline_jdk = variants.get(':'.join(key))
        if baseline_jdk is None and not key[3]:
            baseline_jdk = policy.get('baseline_components', {}).get(coordinate)
        if baseline_jdk:
            origins.append((8, 17, 21).index(baseline_jdk))
        first = min(origins)
        for i, value in enumerate(values):
            jdk = (8, 17, 21)[i]
            exception = policy.get('replacements', {}).get(coordinate, {}).get(str(jdk))
            unsupported = policy.get('unsupported', {}).get(coordinate, {}).get(str(jdk))
            excluded = policy.get('excluded_components', {}).get(coordinate, {}).get(str(jdk))
            if excluded:
                rows.append([display_coordinate, jdk, value.get('version', '') if value else '',
                             'excluded', '', excluded])
                continue
            if unsupported:
                reason = unsupported['reason']
                errors.append(f'JDK {jdk}: unsupported {coordinate}: {reason}')
                rows.append([display_coordinate, jdk, value.get('version', '') if value else '',
                             unsupported.get('status', 'unsupported'), unsupported.get('requires_jdk', ''), reason])
                continue
            if value is None:
                if key in profiles[i]:
                    errors.append(f'JDK {jdk}: {coordinate} is only in profile {profiles[i][key]}, not default coverage')
                if i >= first:
                    alternatives = exception.get('coordinates', []) if exception else []
                    available = {':'.join(k[:2]) for k in effective[i]}
                    if not alternatives or not set(alternatives).issubset(available):
                        errors.append(f'JDK {jdk}: missing {coordinate}')
                rows.append([display_coordinate, jdk, '', 'replacement' if exception else 'absent', '', ''])
                continue
            version = value.get('version', '')
            if not version or '${' in version:
                errors.append(f'JDK {jdk}: unresolved version {coordinate}={version}')
            if key[2] != 'jar':
                rows.append([display_coordinate, jdk, version, key[2], '', ''])
                continue
            group, artifact, _, classifier = key
            relative = Path(group.replace('.', '/')) / artifact / version / f'{artifact}-{version}{"-" + classifier if classifier else ""}.jar'
            jar = repository / relative
            if not jar.exists() and extra_cache:
                jar = extra_cache / str(relative).replace('/', '__')
            if not jar.exists():
                unknown.append(f'JDK {jdk}: {coordinate}:{version}')
                rows.append([display_coordinate, jdk, version, 'unverified-artifact', '', ''])
                continue
            counts = bytecode(jar)
            minimum = max(counts, default=0)
            override = policy.get('optional_bytecode', {}).get(coordinate + ':' + version)
            status = 'bytecode-pass'
            if minimum > jdk:
                if override and override['base_jdk'] <= jdk:
                    status = 'optional-higher-jdk-classes'
                else:
                    errors.append(f'JDK {jdk}: {coordinate}:{version} contains Java {minimum} classes')
                    status = 'bytecode-fail'
            rows.append([display_coordinate, jdk, version, status, minimum, str(jar)])
    if strict:
        errors.extend('Unverified artifact: ' + item for item in unknown)
    return errors, unknown, rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--roots', nargs=3, type=Path, required=True)
    parser.add_argument('--effective', nargs=3, type=Path, required=True)
    parser.add_argument('--policy', type=Path)
    parser.add_argument('--repository', type=Path, default=Path.home() / '.m2/repository')
    parser.add_argument('--extra-cache', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--strict-artifacts', action='store_true')
    args = parser.parse_args()
    policy = json.loads(args.policy.read_text()) if args.policy else {}
    errors, unknown, rows = verify(args.roots, args.effective, policy, args.repository, args.extra_cache, args.strict_artifacts)
    args.output.mkdir(parents=True, exist_ok=True)
    with (args.output / 'components.csv').open('w') as stream:
        writer = csv.writer(stream)
        writer.writerow(['coordinate', 'jdk', 'version', 'status', 'max_base_class_jdk', 'artifact'])
        writer.writerows(rows)
    report = {'errors': errors, 'unverified_artifacts': unknown, 'rows': len(rows)}
    (args.output / 'result.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
    print(f'{len(errors)} errors; {len(unknown)} unverified artifacts; {len(rows)} component/line rows')
    print('\n'.join(errors[:25]))
    return bool(errors)


if __name__ == '__main__':
    sys.exit(main())
