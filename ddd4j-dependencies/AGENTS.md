# ddd4j-dependencies

## 管理第三方版本

在 properties 找版本所有者，再看 dependencyManagement 的显式条目和导入的 BOM。把版本填进管理区不会把库放进子模块的编译类路径。修改版本族后，选一个确实声明该依赖的子模块检查解析树。

配置入口为 [pom.xml](pom.xml)。

## 验证

运行当前版本线的依赖对齐检查后，再构建实际使用新 API 的模块。

```sh
python3 - <<'PYTHON'
import re
import xml.etree.ElementTree as ET
props = ET.parse("ddd4j-dependencies/pom.xml").find("{*}properties")
names = [p.tag.split("}")[-1] for p in props]
def natural(name):
    return [int(v) if v.isdigit() else v.casefold() for v in re.split(r"(\d+)", name)]
assert len(names) == len(set(names)) and names == sorted(names, key=natural)
PYTHON
bash scripts/check-bom-alignment.sh
```
