"""版本归属：源码绑定与独立上游 BOM 参考模型；只在完整 CI 中调用 Maven。"""
from fnmatch import fnmatchcase
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

import verify_spring_version_alignment as spring


POLICY_NAME = 'dependency-version-owners.policy'


def resolve(value, props, visiting=()):
    def replace(match):
        key = match.group(1)
        if key in visiting or key not in props:
            raise ValueError('cyclic or undefined version property: ' + ' -> '.join((*visiting, key)))
        return resolve(props[key], props, (*visiting, key))
    return re.sub(r'\$\{([^}]+)\}', replace, value)


def source(root):
    parent = spring.xml(root / 'pom.xml')
    owner = spring.xml(root / 'ddd4j-dependencies/pom.xml')
    props = {**spring.properties(parent), **spring.properties(owner)}
    props['project.version'] = props.get('revision', '')
    props['pom.version'] = props['project.version']
    imports, bindings = {}, {}
    for dep in owner.findall('./dependencyManagement/dependencies/dependency'):
        group, artifact, kind, classifier = spring.identity(dep)
        value = dep.findtext('version', '')
        if kind == 'pom' and dep.findtext('scope', '') == 'import':
            key = group + ':' + artifact
            if key in imports:
                raise ValueError('duplicate BOM import: ' + key)
            imports[key] = {'version': resolve(value, props), 'reference': value}
        elif value:
            key = (group, artifact, kind, classifier)
            if key in bindings:
                raise ValueError('duplicate central management: ' + ':'.join(key))
            bindings[key] = {'version': resolve(value, props), 'reference': value,
                             'owner': 'central-property'}
    return props, imports, bindings


def load_policy(root):
    return json.loads((root / 'scripts' / POLICY_NAME).read_text())


def matches(rule, coordinate, version, *, classified_artifact=None):
    included = (classified_artifact in rule['includes']
                or any(fnmatchcase(coordinate, pattern) for pattern in rule['includes']))
    return (included and
            not any(fnmatchcase(coordinate, pattern) for pattern in rule.get('excludes', [])) and
            (not rule.get('version_suffix') or version.endswith(rule['version_suffix'])))


def retired_rule(policy, group, artifact):
    coordinate = group + ':' + artifact
    return next((rule for rule in policy.get('retired_dependencies', [])
                 if any(fnmatchcase(coordinate, pattern) for pattern in rule['coordinates'])), None)


def source_retirement_errors(root, projects, policy):
    """检查源码直接声明，覆盖中央目录、模块、profile 和插件依赖；不误判 exclusion。"""
    if not policy.get('retired_dependencies'):
        return []
    errors = []
    paths = {root / 'pom.xml', root / 'ddd4j-dependencies/pom.xml',
             *(root / folder / 'pom.xml' for folder in projects.values())}
    for path in sorted(paths):
        for dep in spring.xml(path).iter('dependency'):
            group, artifact, kind, classifier = spring.identity(dep)
            rule = retired_rule(policy, group, artifact)
            if rule is not None:
                errors.append({'code': 'retired-source-dependency', 'file': str(path.relative_to(root)),
                               'coordinate': group + ':' + artifact, 'type': kind, 'classifier': classifier,
                               'version': dep.findtext('version', ''), 'reason': rule['reason'],
                               'replacement': rule['replacement']})
    return errors


def source_errors(root, projects, policy=None):
    policy = load_policy(root) if policy is None else policy
    props, imports, bindings = source(root)
    errors = source_retirement_errors(root, projects, policy)
    rules = {rule['bom']: rule for rule in policy['boms']}
    if set(imports) != set(rules):
        errors.append({'code': 'bom-owner-coverage', 'unregistered': sorted(set(imports) - set(rules)),
                       'missing': sorted(set(rules) - set(imports))})
    if resolve(props.get('java.version', ''), props) != str(policy['jdk']):
        errors.append({'code': 'source-java-baseline', 'expected': policy['jdk'],
                       'actual': props.get('java.version')})
    previous = -1
    for coordinate, dep in imports.items():
        rule = rules.get(coordinate)
        if rule is None:
            continue
        if dep['reference'] != '${' + rule['property'] + '}':
            errors.append({'code': 'bom-property-owner', 'bom': coordinate,
                           'actual': dep['reference'], 'property': rule['property']})
        if rule['priority'] < previous:
            errors.append({'code': 'bom-owner-priority', 'bom': coordinate,
                           'priority': rule['priority'], 'previous': previous})
        previous = max(previous, rule['priority'])
    for key, expected in policy.get('required_aliases', {}).items():
        if props.get(key) != expected:
            errors.append({'code': 'family-property-alias', 'property': key,
                           'actual': props.get(key), 'expected': expected})
    for key in policy.get('independent_properties', []):
        if key not in props or '${' in props[key]:
            errors.append({'code': 'independent-version-property', 'property': key,
                           'actual': props.get(key)})
    for key, prefix in policy.get('compatible_lines', {}).items():
        actual = resolve(props.get(key, ''), props)
        if not actual.startswith(prefix):
            errors.append({'code': 'jdk17-spring6-component-line', 'property': key,
                           'actual': actual, 'expected_prefix': prefix})
    for group, key in policy.get('family_properties', {}).items():
        if not resolve(props.get(key, ''), props):
            errors.append({'code': 'missing-family-owner', 'group': group, 'property': key})
    if policy.get('update_policy'):
        update = policy['update_policy']
        configured = json.loads((root / update['file']).read_text())
        branch = configured['branches'][update['branch']]
        fixed = {**configured.get('fixed_properties', {}), **branch.get('fixed_properties', {})}
        for key, expected in fixed.items():
            actual = resolve(props.get(key, ''), props)
            if actual != expected:
                errors.append({'code': 'update-policy-version-drift', 'property': key,
                               'actual': actual, 'expected': expected})
    # 检查所有中央版本引用；插件属性也来自根 POM，不能将其误报为未定义。
    for element in spring.xml(root / 'ddd4j-dependencies/pom.xml').iter('version'):
        if element.text:
            resolve(element.text.strip(), props)
    for name, folder in projects.items():
        if name in ('ddd4j', 'ddd4j-dependencies', 'ddd4j-bom'):
            continue
        model = spring.xml(root / folder / 'pom.xml')
        for key, value in spring.properties(model).items():
            if key in props and value != props[key]:
                errors.append({'code': 'module-version-property-shadow', 'module': name,
                               'property': key, 'actual': value, 'expected': props[key]})
    return errors


def collect_references(root, output, maven_args, policy=None):
    """CI 中为每个上游 BOM 构造无框架父 POM 的独立参考模型，不信任中央管理表。"""
    policy = load_policy(root) if policy is None else policy
    _, imports, _ = source(root)
    directory = output / 'upstream-reference-models'
    directory.mkdir(parents=True, exist_ok=True)
    modules = []
    for index, rule in enumerate(policy['boms']):
        name = f'owner-{index:03d}'
        folder = directory / name
        folder.mkdir(exist_ok=True)
        group, artifact = rule['bom'].split(':')
        pom = ET.Element('project', xmlns='http://maven.apache.org/POM/4.0.0')
        for key, value in [('modelVersion', '4.0.0'), ('groupId', 'io.ddd4j.ci'),
                           ('artifactId', name), ('version', '0'), ('packaging', 'pom')]:
            ET.SubElement(pom, key).text = value
        deps = ET.SubElement(ET.SubElement(pom, 'dependencyManagement'), 'dependencies')
        dep = ET.SubElement(deps, 'dependency')
        for key, value in [('groupId', group), ('artifactId', artifact),
                           ('version', imports[rule['bom']]['version']),
                           ('type', 'pom'), ('scope', 'import')]:
            ET.SubElement(dep, key).text = value
        ET.ElementTree(pom).write(folder / 'pom.xml', encoding='unicode')
        modules.append(name)
    aggregator = ET.Element('project', xmlns='http://maven.apache.org/POM/4.0.0')
    for key, value in [('modelVersion', '4.0.0'), ('groupId', 'io.ddd4j.ci'),
                       ('artifactId', 'upstream-reference-models'), ('version', '0'), ('packaging', 'pom')]:
        ET.SubElement(aggregator, key).text = value
    node = ET.SubElement(aggregator, 'modules')
    for name in modules:
        ET.SubElement(node, 'module').text = name
    ET.ElementTree(aggregator).write(directory / 'pom.xml', encoding='unicode')
    paths = []
    env = os.environ.copy()
    env['MAVEN_OPTS'] = (env.get('MAVEN_OPTS', '') + ' -Xmx2048m').strip()
    for offset in range(0, len(modules), 20):
        target = directory / f'effective-{offset // 20 + 1}.xml'
        target.unlink(missing_ok=True)
        command = [str(root / 'mvnw'), '-B', '-ntp', *maven_args, '-f', str(directory / 'pom.xml'),
                   '-pl', ','.join(modules[offset:offset + 20]), spring.HELP_GOAL,
                   '-Doutput=' + str(target)]
        with target.with_suffix('.log').open('w') as log:
            completed = subprocess.run(command, cwd=root, env=env, stdout=log,
                                       stderr=subprocess.STDOUT, timeout=900)
        if completed.returncode or not target.is_file():
            raise ValueError('upstream BOM reference export failed: ' + str(target))
        paths.append(target)
    models = spring.read_models(paths)
    if set(models) != set(modules):
        raise ValueError('incomplete upstream BOM reference coverage')
    return models


def contracts(root, models, policy=None):
    policy = load_policy(root) if policy is None else policy
    _, _, bindings = source(root)
    result = {}
    expected = {f'owner-{index:03d}' for index in range(len(policy['boms']))}
    if set(models) != expected:
        raise ValueError('missing or duplicate upstream reference models')
    for index, rule in enumerate(policy['boms']):
        exported = 0
        required_classifiers = {component for component in rule['includes'] if component.count(':') == 3}
        exported_classifiers = set()
        for dep in models[f'owner-{index:03d}'].findall('./dependencyManagement/dependencies/dependency'):
            key = spring.identity(dep)
            version = dep.findtext('version', '')
            component = ':'.join(key)
            classified_artifact = component if key[3] else None
            if key[2] == 'pom' or not matches(rule, key[0] + ':' + key[1], version,
                                             classified_artifact=classified_artifact):
                continue
            if classified_artifact in required_classifiers:
                exported_classifiers.add(classified_artifact)
            if not version or '${' in version:
                raise ValueError('unresolved upstream component: ' + str(key))
            value = {'version': version, 'owner': rule['bom']}
            if key in result and result[key]['version'] != version:
                raise ValueError('ambiguous component ownership: ' + str(key))
            result[key] = value
            exported += 1
        missing_classifiers = sorted(required_classifiers - exported_classifiers)
        if missing_classifiers:
            raise ValueError('BOM did not export its registered classified artifact: '
                             + rule['bom'] + ': ' + ', '.join(missing_classifiers))
        if not exported and not rule.get('allow_empty', False):
            raise ValueError('BOM did not export its registered component family: ' + rule['bom'])
    # 非 BOM 工件仍受中央源码属性约束；被上游 BOM 管理的工件必须服从其发行契约。
    for key, binding in bindings.items():
        if key[0] == 'io.ddd4j' or binding['version'].startswith(('[', '(')):
            continue
        if key not in result and key[3] and key[2] == 'jar':
            ordinary = result.get((key[0], key[1], 'jar', ''))
            if ordinary is not None:
                result[key] = dict(ordinary)
        if key in result and result[key]['version'] != binding['version']:
            allowed = policy.get('central_overrides', {}).get(':'.join(key))
            if allowed is None:
                allowed = policy.get('central_overrides', {}).get(key[0] + ':' + key[1])
            if allowed != binding['reference']:
                raise ValueError('central version contradicts upstream owner: ' + str(key) +
                                 ' (' + binding['version'] + ' vs ' + result[key]['version'] + ')')
            # 兼容测试 classifier 的中央默认值与普通运行时的上游家族契约分别保留。
            result[key] = {**result[key], 'managed_version': binding['version'],
                           'managed_reference': binding['reference']}
        result.setdefault(key, binding)
    return result


def runtime_expected(policy, props, module, group, artifact, default=None):
    """只允许已登记运行时选择其独立发行线，其他模块继续对照默认归属。"""
    coordinate = group + ':' + artifact
    for rule in policy.get('runtime_overrides', []):
        if rule.get('default_prefixes') and not any(
                (default or '').startswith(prefix) for prefix in rule['default_prefixes']):
            continue
        if any(fnmatchcase(module, pattern) for pattern in rule['modules']) and matches(rule, coordinate, ''):
            return resolve(props[rule['property']], props)
    return None
