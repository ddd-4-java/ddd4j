#!/usr/bin/env python3
"""验证三线显式组件能力覆盖和实际发布 JAR 的基础字节码。

输入必须为本次 Maven 生成的 effective POM；不模拟 Maven BOM 优先级。
缺少 JAR 的结果明确记为未验证；--strict-artifacts 可将其作为失败。
"""
import argparse
import csv
import json
import os
import re
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


def optional_bytecode_isolated(path, jdk, prefixes):
    """Only permit higher-JDK base classes in explicitly named optional packages."""
    if not isinstance(prefixes, list) or not prefixes or any(
            not isinstance(prefix, str) or not prefix.endswith('/') for prefix in prefixes):
        return False
    with zipfile.ZipFile(path) as jar:
        for name in jar.namelist():
            if not name.endswith('.class') or name.startswith('META-INF/versions/') or name.endswith('module-info.class'):
                continue
            with jar.open(name) as stream:
                header = stream.read(8)
            if struct.unpack('>H', header[6:8])[0] - 44 > jdk and not name.startswith(tuple(prefixes)):
                return False
    return True


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


def module_dependencies(root, *, versions=None):
    """列出默认声明的依赖变体，可按目标版本排除明确使用其他版本的模块。"""
    import xml.etree.ElementTree as ET
    result = {}
    for directory, children, files in os.walk(root):
        children[:] = [child for child in children if child not in
                       ('.git', '.codegraph', '.superpowers', 'target', 'node_modules')]
        path = Path(directory) / 'pom.xml'
        if 'pom.xml' not in files or path == root / 'ddd4j-dependencies/pom.xml':
            continue
        project = ET.parse(path).getroot()
        ns = {'m': project.tag.split('}')[0][1:]}
        for dependency in project.findall('m:dependencies/m:dependency', ns):
            row = {node.tag.split('}')[-1]: node.text for node in dependency if len(node) == 0}
            group, artifact = row.get('groupId'), row.get('artifactId')
            if not group or not artifact:
                continue
            key = (group, artifact, row.get('type', 'jar'), row.get('classifier', ''))
            if versions is not None:
                expected = versions.get(key)
                if expected is None:
                    continue
                declared = (row.get('version') or '').strip()
                if (declared and declared not in ('LATEST', 'RELEASE')
                        and '${' not in declared and not declared.startswith(('[', '('))
                        and declared != expected):
                    continue
            result.setdefault(key, []).append(str(path.relative_to(root)))
    return {key: sorted(paths) for key, paths in result.items()}


def pom_properties(path):
    import xml.etree.ElementTree as ET
    if not path.is_file():
        return {}
    root = ET.parse(path).getroot()
    namespace = root.tag.partition('}')[0] + '}' if '}' in root.tag else ''
    properties = root.find(namespace + 'properties')
    return ({item.tag.removeprefix(namespace): (item.text or '').strip() for item in properties}
            if properties is not None else {})


def resolved_property(value, properties):
    for _ in range(10):
        match = re.fullmatch(r'\$\{([^}]+)\}', value or '')
        if match is None or match.group(1) not in properties:
            break
        value = properties[match.group(1)]
    return value


def cached_artifact(relative, repository, extra_cache):
    """只查精确本地坐标；标准 Maven 缓存优先于 flat 候选缓存。"""
    path = repository / relative
    if path.is_file():
        return path
    if extra_cache:
        path = extra_cache / str(relative).replace('/', '__')
        if path.is_file():
            return path
    return None


def published_pom(path, group, artifact, version, packaging):
    """核对发布 POM 的 GAV，仅允许直接 parent 提供 group/version 字面值。"""
    import xml.etree.ElementTree as ET
    if path is None:
        return None
    try:
        project = ET.parse(path).getroot()
    except (OSError, ET.ParseError):
        return None
    namespace = project.tag.partition('}')[0] + '}' if '}' in project.tag else ''
    if project.tag != namespace + 'project':
        return None

    def text(name):
        value = project.findtext('/'.join(namespace + part for part in name.split('/')))
        return value.strip() if value is not None else None

    actual_group = text('groupId')
    actual_version = text('version')
    if actual_group is None:
        actual_group = text('parent/groupId')
    if actual_version is None:
        actual_version = text('parent/version')
    if ((actual_group, text('artifactId'), actual_version) != (group, artifact, version)
            or (text('packaging') or 'jar') != packaging):
        return None
    return project, namespace


def relocated_group_jar(key, version, managed, repository, extra_cache):
    """只接受同 artifact/version 的单跳 group 迁移，且目标须由本线 effective BOM 管理。"""
    group, artifact, _, classifier = key
    if classifier or not re.fullmatch(r'[A-Za-z0-9_.-]+', version):
        return None
    relative = Path(group.replace('.', '/')) / artifact / version / f'{artifact}-{version}.pom'
    source = published_pom(cached_artifact(relative, repository, extra_cache),
                           group, artifact, version, 'pom')
    if source is None:
        return None
    project, namespace = source
    relocation = project.find(namespace + 'distributionManagement/' + namespace + 'relocation')
    if relocation is None:
        return None
    destination = {child.tag.removeprefix(namespace): (child.text or '').strip()
                   for child in relocation}
    target_group = destination.get('groupId', '')
    if (not re.fullmatch(r'[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*', target_group)
            or target_group == group
            or destination.get('artifactId', artifact) != artifact
            or destination.get('version', version) != version
            or managed.get((target_group, artifact, 'jar', ''), {}).get('version') != version):
        return None
    target_relative = Path(target_group.replace('.', '/')) / artifact / version
    target = published_pom(cached_artifact(target_relative / f'{artifact}-{version}.pom',
                                           repository, extra_cache),
                           target_group, artifact, version, 'jar')
    if target is None:
        return None
    target_project, target_namespace = target
    if target_project.find(target_namespace + 'distributionManagement/' + target_namespace + 'relocation') is not None:
        return None
    return cached_artifact(target_relative / f'{artifact}-{version}.jar', repository, extra_cache)


def verify(roots, effective_paths, policy, repository, extra_cache, strict):
    direct = [dependencies(root / 'ddd4j-dependencies/pom.xml') for root in roots]
    profiles = [profile_dependencies(root / 'ddd4j-dependencies/pom.xml') for root in roots]
    effective = [dependencies(path) for path in effective_paths]
    module_usage = {}
    errors, unknown, rows = [], [], []
    for index, root in enumerate(roots):
        jdk = (8, 17, 21)[index]
        component_line = ('1.0.x', '2.0.x', '3.0.x')[index]
        properties = {**pom_properties(root / 'pom.xml'),
                      **pom_properties(root / 'ddd4j-dependencies/pom.xml')}
        for key, dependency in direct[index].items():
            coordinate = ':'.join(key[:2])
            configured = policy.get('dependency_versions', {}).get(coordinate)
            starter_lines = policy.get('boot_starter_versions', {}).get(coordinate)
            if configured is not None:
                expected = configured.get(str(jdk)) if isinstance(configured, dict) else configured
                label = 'dependency'
            elif starter_lines:
                expected = starter_lines[str(jdk)] + '.20260630-SNAPSHOT'
                label = 'Easy4J'
            elif key[0] == 'io.github.easy4j':
                expected = component_line + '.20260630-SNAPSHOT'
                label = 'Easy4J'
            else:
                # Third-party versions follow their provider, never the DDD4J branch name.
                continue
            if not expected:
                errors.append(f'JDK {jdk}: missing version policy for {coordinate}')
                continue
            version = resolved_property(dependency.get('version', ''), properties)
            if version != expected:
                errors.append(f'JDK {jdk}: {label} {coordinate} '
                              f'uses {version or "<missing>"}, expected {expected}')
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
                if value is None:
                    if key in direct[i]:
                        errors.append(f'JDK {jdk}: missing {coordinate}')
                    if key in profiles[i]:
                        errors.append(f'JDK {jdk}: {coordinate} is only in profile {profiles[i][key]}, not default coverage')
                    rows.append([display_coordinate, jdk, '', 'unsupported-absent',
                                 unsupported.get('requires_jdk', ''), reason])
                    continue
                if key not in direct[i]:
                    if i not in module_usage:
                        module_usage[i] = module_dependencies(roots[i])
                    if key not in module_usage[i]:
                        rows.append([display_coordinate, jdk, value.get('version', ''),
                                     'unsupported-catalog-only', unsupported.get('requires_jdk', ''), reason])
                        continue
                errors.append(f'JDK {jdk}: unsupported {coordinate}: {reason}')
                rows.append([display_coordinate, jdk, value.get('version', '') if value else '',
                             unsupported.get('status', 'unsupported'), unsupported.get('requires_jdk', ''), reason])
                continue
            if value is None:
                if key in profiles[i]:
                    errors.append(f'JDK {jdk}: {coordinate} is only in profile {profiles[i][key]}, not default coverage')
                # Third-party BOM catalogs are platform-specific. Require a managed
                # entry on its own declared/baseline line; only Easy4J and explicit
                # coordinate migrations carry a cross-line coverage obligation.
                required = (i >= first if key[0] == 'io.github.easy4j' or exception
                            else key in direct[i] or baseline_jdk == jdk)
                if required:
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
            jar = cached_artifact(relative, repository, extra_cache)
            relocated = jar is None
            if relocated:
                jar = relocated_group_jar(key, version, effective[i], repository, extra_cache)
            if jar is None:
                unknown.append(f'JDK {jdk}: {display_coordinate}:{version}')
                rows.append([display_coordinate, jdk, version, 'unverified-artifact', '', ''])
                continue
            counts = bytecode(jar)
            if relocated and not counts:
                unknown.append(f'JDK {jdk}: {display_coordinate}:{version}')
                rows.append([display_coordinate, jdk, version, 'unverified-artifact', '', str(jar)])
                continue
            minimum = max(counts, default=0)
            if group == 'io.github.easy4j' and not counts:
                errors.append(f'JDK {jdk}: {coordinate}:{version} JAR has no base classes')
                rows.append([display_coordinate, jdk, version, 'bytecode-empty', 0, str(jar)])
                continue
            override = policy.get('optional_bytecode', {}).get(coordinate + ':' + version)
            status = 'bytecode-pass'
            if minimum > jdk:
                if (override and override['base_jdk'] <= jdk
                        and optional_bytecode_isolated(jar, jdk, override.get('allowed_prefixes'))):
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
    missing_versions = {jdk: {} for jdk in (8, 17, 21)}
    candidates = []
    for coordinate, jdk, version, status, _, _ in rows:
        if status != 'unverified-artifact':
            continue
        parts = coordinate.split(':')
        key = (parts[0], parts[1], parts[2], parts[3]) if len(parts) == 4 else (parts[0], parts[1], 'jar', '')
        missing_versions[jdk][key] = version
        candidates.append((jdk, coordinate, version, key))
    usage = {jdk: module_dependencies(root, versions=missing_versions[jdk])
             for jdk, root in zip((8, 17, 21), args.roots)}
    declared_unverified = []
    for jdk, coordinate, version, key in candidates:
        modules = usage[jdk].get(key, [])
        if modules:
            declared_unverified.append({'jdk': jdk, 'coordinate': coordinate,
                                        'version': version, 'modules': modules})
    (args.output / 'module-usage.json').write_text(json.dumps({
        'scope': 'Direct declarations with exact type and classifier in default project dependencies. '
                 'Excludes archived Superpowers evidence and differing literal versions. '
                 'Managed, property and dynamic versions remain candidates until effective-model resolution. '
                 'This report does not change strict catalog validation.',
        'declared_unverified': declared_unverified,
        'declared_unverified_count': len(declared_unverified),
        'catalog_unverified_count': len(unknown),
    }, indent=2, ensure_ascii=False) + '\n')
    print(f'{len(errors)} errors; {len(unknown)} unverified artifacts; {len(rows)} component/line rows')
    print('\n'.join(errors[:25]))
    return bool(errors)


if __name__ == '__main__':
    sys.exit(main())
