#!/usr/bin/env python3
"""JDK 17：验证有效管理表及实际依赖树，阻止平台 BOM 覆盖和依赖家族混版。"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from types import SimpleNamespace
import zipfile

import verify_spring_version_alignment as spring
from verify_dependency_alignment import bytecode


TREE_GOAL = 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree'
PROPERTY_FAMILIES = {
    'org.ow2.asm': 'asm.version', 'tools.jackson.core': 'jackson3.version',
    'org.apache.artemis': 'artemis.version',
}


def compatibility_runtime(module):
    return any(name in module for name in ('quarkus', 'helidon', 'micronaut', 'panache'))


def audit(models, trees, expected_projects):
    errors, entries = [], []
    base = models.get('ddd4j-dependencies')
    if base is None:
        return {'errors': [{'code': 'missing-version-owner'}], 'entries': []}
    props = spring.properties(base)
    reactor = {spring.identity(d)[:2]: d.findtext('version', '') for d in
               base.findall('./dependencyManagement/dependencies/dependency')
               if d.findtext('groupId', '').startswith('io.projectreactor')}
    vertx = {spring.identity(d): d.findtext('version', '') for d in
             base.findall('./dependencyManagement/dependencies/dependency')
             if d.findtext('groupId', '') == 'io.vertx'}
    if ('io.projectreactor', 'reactor-core') not in reactor:
        errors.append({'code': 'missing-reactor-management'})
    for key in (*PROPERTY_FAMILIES.values(), 'netty.version', 'netty-compat4.version',
                'vertx.version', 'vertx-compat4.version'):
        if not props.get(key) or '${' in props[key]:
            errors.append({'code': 'missing-shared-property', 'property': key})
    if set(trees) != set(expected_projects):
        errors.append({'code': 'tree-coverage', 'actual': sorted(trees), 'expected': sorted(expected_projects)})

    def check(module, group, artifact, version, scope, classifier='', path=None, kind='jar'):
        expected = None
        if group in PROPERTY_FAMILIES:
            expected = props.get(PROPERTY_FAMILIES[group])
        elif group == 'ch.qos.logback' and artifact in ('logback-core', 'logback-classic'):
            expected = props.get('logback.version')
        elif group == 'org.apache.activemq' and artifact.startswith('artemis-'):
            # 官方新 BOM 暂保留旧组名的 relocation 元数据；实际声明必须迁移。
            expected = props.get('artemis.version') if scope == 'managed' else 'migrate to org.apache.artemis:' + props.get('artemis.version', '')
        elif group.startswith('io.projectreactor'):
            expected = reactor.get((group, artifact))
        elif group == 'io.vertx':
            expected = vertx.get((group, artifact, kind, classifier))
            if expected and re.match(r'^[45]\.', expected) and any(name in module for name in ('quarkus', 'micronaut', 'panache')) and (scope == 'resolved' or version.startswith('4.')):
                expected = props.get('vertx-compat4.version')
        elif group == 'io.netty' and artifact.startswith('netty-tcnative'):
            expected = props.get('netty-tcnative.version')
        elif group == 'io.netty':
            # 兼容模块的管理目录仍可包含仅存在于 4.2 的未使用组件。
            # 实际类路径必须完整采用该运行时支持的一条线，不能如此豁免。
            compat = compatibility_runtime(module) and (scope == 'resolved' or version.startswith('4.1.'))
            expected = props.get('netty-compat4.version' if compat else 'netty.version')
        if expected is not None:
            item = dict(module=module, scope=scope, coordinate=group + ':' + artifact,
                        classifier=classifier, actual=version, expected=expected)
            if path is not None:
                item['path'] = path
            entries.append(item)
            if version != expected:
                errors.append(dict(item, code='resolved-version' if scope == 'resolved' else 'managed-version'))

    for module, model in models.items():
        for scope, xpath in [('managed', './dependencyManagement/dependencies/dependency'),
                             ('declared', './dependencies/dependency')]:
            for d in model.findall(xpath):
                if d.findtext('type', 'jar') == 'pom' or d.findtext('artifactId', '').endswith('-bom'):
                    continue
                group, artifact, kind, classifier = spring.identity(d)
                check(module, group, artifact, d.findtext('version', ''), scope, classifier, kind=kind)
    for module, tree in trees.items():
        if tree.get('artifactId') != module or module not in models:
            errors.append({'code': 'tree-identity', 'module': module, 'actual': tree.get('artifactId')})
            continue

        def visit(node, path):
            coordinate = node.get('groupId', '') + ':' + node.get('artifactId', '')
            path = path + [coordinate + ':' + node.get('version', '')]
            if node is not tree:
                check(module, node.get('groupId', ''), node.get('artifactId', ''),
                      node.get('version', ''), 'resolved', node.get('classifier', ''), path, node.get('type', 'jar'))
            for child in node.get('children', []):
                visit(child, path)
        visit(tree, [])
    return {'errors': errors, 'entries': entries, 'projects': len(models), 'trees': len(trees)}


def audit_bytecode(trees, repository):
    artifacts, errors, rows = set(), [], []

    def visit(node):
        group, artifact, version = (node.get(key, '') for key in ('groupId', 'artifactId', 'version'))
        kind, classifier = node.get('type', 'jar'), node.get('classifier', '')
        if group != 'io.ddd4j' and kind in ('jar', 'test-jar', 'bundle', 'maven-plugin'):
            classifier = classifier or ('tests' if kind == 'test-jar' else '')
            artifacts.add((group, artifact, version, classifier))
        for child in node.get('children', []):
            visit(child)
    for tree in trees.values():
        visit(tree)
    for group, artifact, version, classifier in sorted(artifacts):
        suffix = '-' + classifier if classifier else ''
        path = repository / group.replace('.', '/') / artifact / version / (artifact + '-' + version + suffix + '.jar')
        row = dict(coordinate=group + ':' + artifact, version=version, classifier=classifier, path=str(path))
        try:
            counts = bytecode(path)
            row.update(max_jdk=max(counts, default=0), sha256=hashlib.sha256(path.read_bytes()).hexdigest())
            if any(jdk > 17 for jdk in counts):
                errors.append(dict(row, code='bytecode-jdk'))
        except (OSError, ValueError, zipfile.BadZipFile) as error:
            errors.append(dict(row, code='bytecode-unverified', message=str(error)))
        rows.append(row)
    return {'errors': errors, 'artifacts': rows}


def run(args):
    root, output = args.root.resolve(), args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    result = {'status': 'FAIL', 'errors': []}
    try:
        projects, paths = spring.discover_projects(root)
        before = spring.fingerprint(paths)
        spring_output = output / 'spring'
        status = spring.run(SimpleNamespace(root=root, output=spring_output, line='2.0.x',
                                            batch_size=25, maven_arg=args.maven_arg))
        spring_result = json.loads((spring_output / 'summary.json').read_text())
        if status:
            result['errors'].append({'code': 'spring-alignment', 'errors': spring_result['errors']})
        models = spring.read_models([spring_output / p for p in spring_result.get('effective_pom_files', [])])
        tree_dir = output / 'trees'
        tree_dir.mkdir(exist_ok=True)
        for path in tree_dir.glob('*.json'):
            path.unlink()
        command = [str(root / 'mvnw'), '-B', '-ntp', *args.maven_arg, TREE_GOAL,
                   '-DoutputType=json', '-DoutputFile=' + str(tree_dir / '${project.artifactId}.json')]
        with (output / 'dependency-trees.log').open('w') as log:
            completed = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT,
                                       timeout=1200)
        if completed.returncode:
            raise ValueError('dependency tree resolution failed; see dependency-trees.log')
        trees = {p.stem: json.loads(p.read_text()) for p in tree_dir.glob('*.json')}
        audited = audit(models, trees, set(projects))
        result['errors'].extend(audited.pop('errors'))
        result.update(audited)
        if args.check_bytecode:
            checked = audit_bytecode(trees, args.repository.resolve())
            result['errors'].extend(checked['errors'])
            (output / 'bytecode.json').write_text(json.dumps(checked, ensure_ascii=False, indent=2) + '\n')
        after = spring.fingerprint(paths)
        if before != after:
            result['errors'].append({'code': 'source-changed'})
        result.update(source_pom_sha256=before,
                      checker_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
        result['status'] = 'FAIL' if result['errors'] else 'PASS'
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        result['errors'].append({'code': 'incomplete-verification', 'message': str(error)})
    (output / 'summary.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(f"Dependency families: {result['status']}; {len(result['errors'])} errors; {output}")
    for error in result['errors'][:15]:
        print(json.dumps(error, ensure_ascii=False))
    return 0 if result['status'] == 'PASS' else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path.cwd())
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--maven-arg', action='append', default=[])
    parser.add_argument('--check-bytecode', action='store_true')
    parser.add_argument('--repository', type=Path, default=Path.home() / '.m2/repository')
    return run(parser.parse_args())


if __name__ == '__main__':
    sys.exit(main())
