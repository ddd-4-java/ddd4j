"""在对应 JDK 下隔离运行现有 Web 核心版本测试（Maven 依赖不可解析期间）。"""

from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "ddd4j-web/ddd4j-web-core/src"
M2 = Path.home() / ".m2/repository"
DEPENDENCIES = [
    M2 / "com/fasterxml/jackson/core/jackson-annotations/2.22/jackson-annotations-2.22.jar",
    M2 / "org/junit/jupiter/junit-jupiter-api/5.8.2/junit-jupiter-api-5.8.2.jar",
    M2 / "org/opentest4j/opentest4j/1.2.0/opentest4j-1.2.0.jar",
    M2 / "org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar",
    M2 / "org/junit/platform/junit-platform-commons/1.8.2/junit-platform-commons-1.8.2.jar",
]


def current_jdk():
    pom = ET.parse(ROOT / "pom.xml").getroot()
    namespace = pom.tag.partition("}")[0] + "}" if "}" in pom.tag else ""
    version = pom.findtext("./" + namespace + "properties/" + namespace + "java.version")
    return Path(subprocess.check_output(["/usr/libexec/java_home", "-v", version], text=True).strip())


class WebApiVersionSuiteTest(unittest.TestCase):
    def test_existing_version_and_context_tests(self):
        for dependency in DEPENDENCIES:
            self.assertTrue(dependency.is_file(), dependency)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            stub = root / "io/ddd4j/kit/lang/StrKit.java"
            stub.parent.mkdir(parents=True)
            stub.write_text('package io.ddd4j.kit.lang; public final class StrKit { public static boolean isBlank(String s){return s==null||s.trim().isEmpty();} public static boolean isNotBlank(String s){return !isBlank(s);} public static boolean isEmpty(String s){return s==null||s.isEmpty();} public static boolean isNotEmpty(String s){return !isEmpty(s);} }')
            logger = root / "org/slf4j/Logger.java"
            logger.parent.mkdir(parents=True)
            logger.write_text('package org.slf4j; public interface Logger { void warn(String s,Object... v); void debug(String s,Object... v); }')
            factory = root / "org/slf4j/LoggerFactory.java"
            factory.write_text('package org.slf4j; public final class LoggerFactory { public static Logger getLogger(Class<?> c){return new Logger(){public void warn(String s,Object... v){} public void debug(String s,Object... v){}};} }')
            runner = root / "WebVersionSuiteRunner.java"
            runner.write_text('''import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
public class WebVersionSuiteRunner {
    public static void main(String[] args) throws Exception {
        int count = 0;
        for (String name : args) {
            Class<?> type = Class.forName(name);
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object instance = constructor.newInstance();
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Test.class)) {
                    method.setAccessible(true);
                    try { method.invoke(instance); }
                    catch (InvocationTargetException failure) { throw new AssertionError(name + "#" + method.getName(), failure.getCause()); }
                    count++;
                }
            }
        }
        System.out.println("web version tests=" + count);
    }
}''')
            main = BASE / "main/java/io/ddd4j/web/core"
            tests = BASE / "test/java/io/ddd4j/web/core"
            context = ["ClientIpResolver", "DefaultClientIpResolver", "RequestIdGenerator", "WebRequestData",
                       "WebRequestContext", "WebRequestContextFactory"]
            sources = [stub, logger, factory, runner, main / "error/WebStatusException.java",
                       *(main / "context" / (name + ".java") for name in context),
                       *sorted((main / "version").glob("*.java")),
                       *sorted((tests / "version").glob("*Test.java")),
                       tests / "context/WebRequestContextApiVersionTest.java"]
            classes = root / "classes"
            classes.mkdir()
            classpath = ":".join(map(str, DEPENDENCIES))
            jdk = current_jdk()
            compiled = subprocess.run([str(jdk / "bin/javac"), "-classpath", classpath, "-d", str(classes),
                                       *map(str, sources)], capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            test_types = ["io.ddd4j.web.core.version." + path.stem for path in sorted((tests / "version").glob("*Test.java"))]
            test_types.append("io.ddd4j.web.core.context.WebRequestContextApiVersionTest")
            execution = subprocess.run([str(jdk / "bin/java"), "-classpath", str(classes) + ":" + classpath,
                                        "WebVersionSuiteRunner", *test_types], capture_output=True, text=True)
            self.assertEqual(0, execution.returncode, execution.stderr)
            self.assertIn("web version tests=27", execution.stdout)


if __name__ == "__main__":
    unittest.main()
