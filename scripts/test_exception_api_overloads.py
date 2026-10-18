"""业务异常保留 ApiCode 与 CustomApiCode 两种二参构造器。"""

from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "ddd4j-core/src/main/java/io/ddd4j/core/exception"
LOMBOK = Path.home() / ".m2/repository/org/projectlombok/lombok/1.18.46/lombok-1.18.46.jar"


def current_jdk():
    pom = ET.parse(ROOT / "pom.xml").getroot()
    namespace = pom.tag.partition("}")[0] + "}" if "}" in pom.tag else ""
    version = pom.findtext("./" + namespace + "properties/" + namespace + "java.version")
    return Path(subprocess.check_output(["/usr/libexec/java_home", "-v", version], text=True).strip())


class ExceptionApiOverloadsTest(unittest.TestCase):
    def test_both_code_types_keep_runtime_and_checked_constructors(self):
        jdk = current_jdk()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            api = root / "io/ddd4j/core/api/CustomApiCode.java"
            api.parent.mkdir(parents=True)
            api.write_text("package io.ddd4j.core.api; public interface CustomApiCode { Integer getCode(); String getReason(); }\n")
            enum = root / "io/ddd4j/core/api/ApiCode.java"
            enum.write_text('package io.ddd4j.core.api; public enum ApiCode implements CustomApiCode { FAIL; public Integer getCode() { return 409; } public String getReason() { return "reason"; } }\n')
            i18n = root / "io/ddd4j/core/util/I18nKit.java"
            i18n.parent.mkdir(parents=True)
            i18n.write_text("package io.ddd4j.core.util; public final class I18nKit { public static String get(String text, Object... values) { return text; } }\n")
            probe = root / "ExceptionOverloadProbe.java"
            probe.write_text('import io.ddd4j.core.api.*; import io.ddd4j.core.exception.*; '
                             'public class ExceptionOverloadProbe { public static void main(String[] args) throws Exception { '
                             'BizRuntimeException.class.getConstructor(ApiCode.class, String.class); '
                             'BizRuntimeException.class.getConstructor(CustomApiCode.class, String.class); '
                             'BizCheckedException.class.getConstructor(ApiCode.class, String.class); '
                             'BizCheckedException.class.getConstructor(CustomApiCode.class, String.class); '
                             'CustomApiCode custom = new CustomApiCode() { public Integer getCode() { return 410; } '
                             'public String getReason() { return "custom"; } }; '
                             'if (new BizRuntimeException(ApiCode.FAIL, "key").getCode() != 409) throw new AssertionError(); '
                             'if (new BizRuntimeException(custom, "key").getCode() != 410) throw new AssertionError(); '
                             'if (new BizCheckedException(ApiCode.FAIL, "key").getCode() != 409) throw new AssertionError(); '
                             'if (new BizCheckedException(custom, "key").getCode() != 410) throw new AssertionError(); '
                             'try { new BizRuntimeException((ApiCode) null, "key"); throw new AssertionError(); } '
                             'catch (NullPointerException expected) { } '
                             'try { new BizCheckedException((CustomApiCode) null, "key"); throw new AssertionError(); } '
                             'catch (NullPointerException expected) { } '
                             'System.out.println("4 exception overload calls passed"); } }\n')
            classes = root / "classes"
            classes.mkdir()
            sources = [api, enum, i18n, SOURCE / "BizRuntimeException.java",
                       SOURCE / "BizCheckedException.java", probe]
            compiled = subprocess.run([str(jdk / "bin/javac"), "-classpath", str(LOMBOK),
                                       "-d", str(classes), *map(str, sources)],
                                      capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            execution = subprocess.run([str(jdk / "bin/java"), "-classpath", str(classes),
                                        "ExceptionOverloadProbe"], capture_output=True, text=True)
            self.assertEqual(0, execution.returncode, execution.stderr)
            self.assertIn("4 exception overload calls passed", execution.stdout)


if __name__ == "__main__":
    unittest.main()
