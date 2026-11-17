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
from urllib.parse import quote
import zipfile

import verify_spring_version_alignment as spring
from verify_dependency_alignment import bytecode


TREE_GOAL = 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree'
PROPERTY_FAMILIES = {
    'org.ow2.asm': 'asm.version', 'tools.jackson.core': 'jackson3.version',
    'org.apache.artemis': 'artemis.version',
}
SECURITY_FAMILIES = {
    ('net.minidev', 'json-smart'): ('json-smart.version', '2.6.1'),
    ('net.minidev', 'accessors-smart'): ('json-smart.version', '2.6.1'),
    ('com.github.luben', 'zstd-jni'): ('zstd-jni.version', '1.5.7-28'),
    ('org.json', 'json'): ('json.version', '20260814'),
    ('ch.qos.logback', 'logback-classic'): ('logback.version', '1.6.7'),
    ('ch.qos.logback', 'logback-core'): ('logback.version', '1.6.7'),
    ('org.glassfish.jaxb', 'jaxb-runtime'): ('glassfish-jaxb.version', '4.0.9'),
    ('org.glassfish.jaxb', 'jaxb-core'): ('glassfish-jaxb.version', '4.0.9'),
    ('org.glassfish.jaxb', 'txw2'): ('glassfish-jaxb.version', '4.0.9'),
    ('org.glassfish.jaxb', 'jaxb-xjc'): ('glassfish-jaxb.version', '4.0.9'),
    ('org.glassfish.jaxb', 'jaxb-jxc'): ('glassfish-jaxb.version', '4.0.9'),
}
for _artifact in ('bcprov-lts8on', 'bcutil-lts8on', 'bcpkix-lts8on'):
    SECURITY_FAMILIES[('org.bouncycastle', _artifact)] = ('bouncycastle-lts.version', '2.73.13')
MICROMETER_BASELINES = {'micrometer.version': '1.17.2', 'micrometer-tracing.version': '1.7.2'}
MICROMETER_METRICS = {
    'micrometer-core', 'micrometer-commons', 'micrometer-observation',
    'micrometer-observation-test', 'micrometer-test', 'micrometer-jakarta9',
    'micrometer-java11', 'micrometer-java21', 'micrometer-jetty11', 'micrometer-jetty12',
}


def micrometer_property(artifact):
    # 同一 groupId 下指标、追踪和 context-propagation 的发行版本互相独立。
    if artifact.startswith('micrometer-tracing'):
        return 'micrometer-tracing.version'
    if artifact in MICROMETER_METRICS or artifact.startswith('micrometer-registry-'):
        return 'micrometer.version'
    return None


def compatibility_runtime(module):
    return any(name in module for name in ('quarkus', 'helidon', 'panache'))


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
    shared = {spring.identity(d): d.findtext('version', '') for d in
              base.findall('./dependencyManagement/dependencies/dependency')
              if d.findtext('groupId', '') in ('io.opentelemetry', 'io.micrometer',
                                               'software.amazon.awssdk', 'org.jdbi',
                                               'org.glassfish.hk2', 'org.glassfish.hk2.external',
                                               'io.dropwizard.metrics', 'org.glassfish.jaxb',
                                               'org.hibernate.orm', 'io.agroal', 'org.graalvm.sdk',
                                               'org.apache.maven') or
              d.findtext('groupId', '').startswith('org.glassfish.jersey')}
    if ('io.projectreactor', 'reactor-core') not in reactor:
        errors.append({'code': 'missing-reactor-management'})
    jpa = next((d.findtext('version', '') for d in base.findall('./dependencyManagement/dependencies/dependency')
                if spring.identity(d)[:2] == ('jakarta.persistence', 'jakarta.persistence-api')), '')
    if not props.get('hibernate.version', '').startswith('6.6.') or jpa != '3.1.0':
        errors.append({'code': 'spring6-orm-baseline', 'hibernate': props.get('hibernate.version'),
                       'jpa': jpa, 'expected': 'Hibernate 6.6.x / JPA 3.1.0'})
    for key, minimum in {*SECURITY_FAMILIES.values(), ('shiro.version', '3.0.2'),
                         *MICROMETER_BASELINES.items()}:
        actual = props.get(key, '')
        version = tuple(int(part) for part in re.findall(r'\d+', actual))
        if not re.fullmatch(r'\d+(?:[.-]\d+)*', actual) or version < tuple(
                int(part) for part in re.findall(r'\d+', minimum)):
            errors.append({'code': 'security-baseline', 'property': key,
                           'actual': actual, 'minimum': minimum})
    for key in (*PROPERTY_FAMILIES.values(), 'netty.version', 'netty-4_1.version',
                'vertx.version', 'vertx-compat4.version'):
        if not props.get(key) or '${' in props[key]:
            errors.append({'code': 'missing-shared-property', 'property': key})
    if set(trees) != set(expected_projects):
        errors.append({'code': 'tree-coverage', 'actual': sorted(trees), 'expected': sorted(expected_projects)})

    def check(module, group, artifact, version, scope, classifier='', path=None, kind='jar'):
        expected = None
        if (group, artifact) == ('com.vaadin.external.google', 'android-json') and scope != 'managed':
            errors.append({'code': 'obsolete-json-provider', 'module': module, 'scope': scope,
                           'coordinate': group + ':' + artifact, 'actual': version, 'path': path})
        if (group, artifact) == ('org.ehcache', 'ehcache') and scope != 'managed' and classifier != 'jakarta':
            errors.append({'code': 'javax-jaxb-consumer', 'module': module, 'scope': scope,
                           'coordinate': group + ':' + artifact, 'classifier': classifier, 'path': path})
        if group == 'io.micrometer' and micrometer_property(artifact):
            # 独立校验中央有效表本身，不能以已被 Hibernate 等 BOM 覆盖的表为正确基线。
            expected = props.get(micrometer_property(artifact))
        elif (group, artifact) in SECURITY_FAMILIES:
            expected = props.get(SECURITY_FAMILIES[(group, artifact)][0])
        elif group == 'org.apache.shiro':
            expected = props.get('shiro.version')
        elif group in PROPERTY_FAMILIES:
            expected = props.get(PROPERTY_FAMILIES[group])
        elif (group, artifact, kind, classifier) in shared:
            expected = shared[(group, artifact, kind, classifier)]
            if group.startswith('org.glassfish.jersey') and 'helidon' in module and scope != 'managed':
                expected = props.get('helidon-jersey.version')
            elif group in ('org.hibernate.orm', 'io.agroal') and 'panache' in module and scope != 'managed':
                expected = props.get('quarkus-hibernate.version' if group == 'org.hibernate.orm' else 'quarkus-agroal.version')
        elif group == 'ch.qos.logback' and artifact in ('logback-core', 'logback-classic'):
            expected = props.get('logback.version')
        elif group == 'jakarta.persistence' and artifact == 'jakarta.persistence-api':
            expected = props.get('jakarta.persistence-api.version') if scope != 'managed' and any(
                name in module for name in ('quarkus', 'panache')) else jpa
        elif group == 'org.apache.activemq' and artifact.startswith('artemis-'):
            # 官方新 BOM 暂保留旧组名的 relocation 元数据；实际声明必须迁移。
            expected = props.get('artemis.version') if scope == 'managed' else 'migrate to org.apache.artemis:' + props.get('artemis.version', '')
        elif group.startswith('io.projectreactor'):
            expected = reactor.get((group, artifact))
        elif group == 'io.vertx':
            expected = vertx.get((group, artifact, kind, classifier))
            if expected and re.match(r'^[45]\.', expected) and any(name in module for name in ('quarkus', 'panache')) and scope != 'managed':
                expected = props.get('vertx-compat4.version')
        elif group == 'io.netty' and artifact.startswith('netty-tcnative'):
            expected = props.get('netty-tcnative.version')
        elif group == 'io.netty':
            # 管理表统一归属中央；兼容运行时通过排除和显式依赖选用 4.1。
            compat = compatibility_runtime(module) and scope != 'managed'
            expected = props.get('netty-4_1.version' if compat else 'netty.version')
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


def source_management_errors(root, projects):
    return [{'code': 'module-dependency-management', 'module': name,
             'message': 'use central management, exclusions and explicit dependencies'}
            for name, folder in projects.items()
            if name not in ('ddd4j-dependencies', 'ddd4j-bom') and
            spring.xml(root / folder / 'pom.xml').find('dependencyManagement') is not None]


def source_bom_errors(root):
    owner = spring.xml(root / 'ddd4j-dependencies/pom.xml')
    props = spring.properties(owner)
    errors = []
    for key, minimum in MICROMETER_BASELINES.items():
        actual = props.get(key, '')
        if not re.fullmatch(r'\d+\.\d+\.\d+', actual) or tuple(map(int, actual.split('.'))) < tuple(
                map(int, minimum.split('.'))):
            errors.append({'code': 'micrometer-source-baseline', 'property': key,
                           'actual': actual, 'minimum': minimum})
    imports = [dep for dep in owner.findall('./dependencyManagement/dependencies/dependency')
               if dep.findtext('type', 'jar') == 'pom' and dep.findtext('scope', '') == 'import']
    platforms = {'hibernate-platform', 'spring-boot-dependencies', 'debezium-bom',
                 'dropwizard-bom', 'helidon-bom', 'micronaut-platform', 'quarkus-bom'}
    for artifact, key in (('micrometer-bom', 'micrometer.version'),
                          ('micrometer-tracing-bom', 'micrometer-tracing.version')):
        found = [(i, dep) for i, dep in enumerate(imports)
                 if dep.findtext('groupId', '') == 'io.micrometer' and
                 dep.findtext('artifactId', '') == artifact]
        if len(found) != 1:
            errors.append({'code': 'micrometer-bom-count', 'artifact': artifact, 'actual': len(found)})
            continue
        index, dep = found[0]
        if dep.findtext('version', '') != '${' + key + '}':
            errors.append({'code': 'micrometer-bom-owner', 'artifact': artifact, 'property': key})
        earlier = [d.findtext('artifactId', '') for d in imports[:index]
                   if d.findtext('artifactId', '') in platforms]
        if earlier:
            errors.append({'code': 'micrometer-bom-priority', 'artifact': artifact, 'earlier': earlier})
    return errors


def inventories(models, trees):
    result = {'unresolved_management': []}
    for scope in ('managed', 'reactor'):
        components, origins = {}, {}

        def add(group, artifact, version, module):
            if group == 'io.ddd4j':
                return
            if not version or '${' in version or version.startswith(('[', '(')) or version in ('LATEST', 'RELEASE'):
                if scope == 'reactor':
                    raise ValueError(f'unresolved actual dependency: {group}:{artifact}:{version}')
                result['unresolved_management'].append(dict(module=module, group=group,
                                                           artifact=artifact, version=version))
                return
            purl = f"pkg:maven/{quote(group, safe='.')}/{quote(artifact, safe='')}@{quote(version, safe='.-_')}"
            components[purl] = dict(type='library', group=group, name=artifact, version=version,
                                    purl=purl, **{'bom-ref': purl})
            origins.setdefault(purl, set()).add(module)

        if scope == 'managed':
            for module, model in models.items():
                for dep in model.findall('./dependencyManagement/dependencies/dependency'):
                    add(dep.findtext('groupId', ''), dep.findtext('artifactId', ''), dep.findtext('version', ''), module)
        else:
            def visit(node, module):
                add(node.get('groupId', ''), node.get('artifactId', ''), node.get('version', ''), module)
                for child in node.get('children', []):
                    visit(child, module)
            for module, tree in trees.items():
                visit(tree, module)
        result[scope] = dict(bomFormat='CycloneDX', specVersion='1.5', version=1,
                             components=[components[key] for key in sorted(components)])
        result[scope + '_origins'] = {key: sorted(value) for key, value in sorted(origins.items())}
    return result


def run(args):
    root, output = args.root.resolve(), args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    result = {'status': 'FAIL', 'errors': []}
    try:
        projects, paths = spring.discover_projects(root)
        result['errors'].extend(source_management_errors(root, projects))
        result['errors'].extend(source_bom_errors(root))
        if getattr(args, 'source_only', False):
            result.update(projects=len(projects), verification='source-only')
            result['status'] = 'FAIL' if result['errors'] else 'PASS'
            (output / 'summary.json').write_text(json.dumps(result, indent=2) + '\n')
            print(f"Dependency source: {result['status']}; {len(result['errors'])} errors; {output}")
            for error in result['errors'][:15]:
                print(json.dumps(error, ensure_ascii=False))
            return int(bool(result['errors']))
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
        inventory = inventories(models, trees)
        for scope in ('managed', 'reactor'):
            (output / (scope + '-bom.cdx.json')).write_text(json.dumps(inventory[scope], indent=2) + '\n')
            (output / (scope + '-component-origins.json')).write_text(json.dumps(inventory[scope + '_origins'], indent=2) + '\n')
        (output / 'unresolved-managed-policies.json').write_text(json.dumps(inventory['unresolved_management'], indent=2) + '\n')
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
    parser.add_argument('--source-only', action='store_true',
                        help='只检查源码版本归属和 BOM 顺序，不调用 Maven')
    parser.add_argument('--root', type=Path, default=Path.cwd())
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--maven-arg', action='append', default=[])
    parser.add_argument('--check-bytecode', action='store_true')
    parser.add_argument('--repository', type=Path, default=Path.home() / '.m2/repository')
    return run(parser.parse_args())


if __name__ == '__main__':
    sys.exit(main())
