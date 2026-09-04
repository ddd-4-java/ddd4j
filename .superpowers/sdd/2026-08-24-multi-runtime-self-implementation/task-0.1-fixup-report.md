# Task 0.1 Fixup Report: Remove leftover fuin-cqrs4j.version property

- **Date:** 2026-08-24
- **Branch:** `feature/2.0.x`
- **Base commit:** `6ff92108` (docs: 删除 fuin 依赖表述，标注为外部参考链接)
- **Fixup commit:** `b8fec37e` (build: remove leftover fuin-cqrs4j.version property)

## Before / After Line Context

`ddd4j-dependencies/pom.xml`, properties block (alphabetical), line 85 before the edit:

```xml
82          <camel.version>4.20.0</camel.version>
83          <canal.version>1.1.8</canal.version>
84          <cas-client.version>4.1.1</cas-client.version>
85          <fuin-cqrs4j.version>0.6.0</fuin-cqrs4j.version>      <-- deleted
86          <cassandra-driver.version>4.19.2</cassandra-driver.version>
87          <cglib.version>3.3.0</cglib.version>
```

After the edit, `<cassandra-driver.version>` directly follows `<cas-client.version>`. No
other lines, indentation, or comments were touched.

## Diff Stats

```
 ddd4j-dependencies/pom.xml | 1 -
 1 file changed, 1 deletion(-)
```

Full diff:

```diff
diff --git a/ddd4j-dependencies/pom.xml b/ddd4j-dependencies/pom.xml
index 0cd8121c..451d9db4 100644
--- a/ddd4j-dependencies/pom.xml
+++ b/ddd4j-dependencies/pom.xml
@@ -82,7 +82,6 @@
         <camel.version>4.20.0</camel.version>
         <canal.version>1.1.8</canal.version>
         <cas-client.version>4.1.1</cas-client.version>
-        <fuin-cqrs4j.version>0.6.0</fuin-cqrs4j.version>
         <cassandra-driver.version>4.19.2</cassandra-driver.version>
         <cglib.version>3.3.0</cglib.version>
         <checker-framework.version>3.55.1</checker-framework.version>
```

## Verification

### Pre-edit dead-property check

`grep -rn "fuin-cqrs4j" --include=*.{xml,gradle,properties,java,kt}` across the repo
returned exactly one hit: the property's own definition at line 85. Confirmed dead
(zero consumers), so deletion is build-safe.

### Grep gate (Task 9.2 prerequisite)

```
$ grep -c "fuin" ddd4j-dependencies/pom.xml
0
```

Zero matches — the pom now passes the zero-fuin grep gate.

### BOM install

```
$ ./mvnw -pl ddd4j-dependencies install -DskipTests
...
[INFO] Installing ... ddd4j-dependencies-2.0.x.20260630-SNAPSHOT.pom
[INFO] BUILD SUCCESS
[INFO] Total time:  2.435 s
```

BUILD SUCCESS — the BOM resolves without the property, as expected for an unused
property.

## Self-Review

- [x] Only deleted the `<fuin-cqrs4j.version>0.6.0</fuin-cqrs4j.version>` line
- [x] Did not touch any other property
- [x] Did not change indentation of surrounding properties (diff shows a single `-` line)
- [x] Did not delete property comments describing other properties (none adjacent)
- [x] BOM install still succeeds (`BUILD SUCCESS`, 2.4s)
- [x] `grep -c "fuin" ddd4j-dependencies/pom.xml` = 0
- [x] Single commit containing exactly the one-file, one-line change

## Notes / Concerns

None. Two untracked plan documents under `docs/superpowers/plans/` were present in the
working tree before this fixup and were intentionally left uncommitted (not part of this
task's scope).
