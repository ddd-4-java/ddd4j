# TrueLicense 1.33 合成兼容夹具

本目录的 `legacy-v1.lic` 由 `de.schlichtherle.truelicense:truelicense-core:1.33`
在隔离的临时 classpath 中生成，`legacy-public.jks` 只包含对应的合成 DSA 公钥证书。
私钥库只在 `/tmp` 中用于一次性生成，没有进入仓库。

- subject：`ddd4j-legacy-fixture`
- alias：`ddd4j-legacy`
- 公钥库类型：JKS
- 历史签名密钥：1024-bit DSA（仅为兼容 1.33 的 SHA-1/DSA 签名限制）
- 公钥库测试口令：`ddd4j-legacy-store-password`
- 有效期：2026-01-01 至 2099-12-31
- 生成运行时：TrueLicense 1.33 + truelicense-xml 1.33 + commons-codec 1.6

该夹具只用于证明 TrueLicense 4.1.4 `truelicense-v1` 对旧 1.x 文件格式的消费兼容性；
最终 Maven 依赖和测试运行 classpath 不包含 1.x 运行库。

生成时曾尝试 2048-bit DSA，JDK 8 按预期拒绝 1.33 的 SHA-1/DSA 签名并报告
`InvalidKeyException: The security strength of SHA-1 digest algorithm is not sufficient for this key size`。
因此该弱 1024-bit 密钥只存在于固定历史夹具中；4.x 新签发回归仍使用 2048-bit DSA，
本迁移不修改 JDK 全局安全策略，也不推荐任何新生产许可证继续采用该旧算法。

## 可复现生成步骤

目录中的 `LegacyFixtureGenerator.java.txt` 是隔离生成器源码；复现时先复制为临时目录中的
`LegacyFixtureGenerator.java`。生成器使用内存 Preferences，不接触操作系统用户节点。

```bash
fixture_tmp=$(mktemp -d /tmp/ddd4j-truelicense-legacy.XXXXXX)
jdk8_bin=/Users/wandl/Library/Java/JavaVirtualMachines/corretto-1.8.0_504/Contents/Home/bin

cp LegacyFixtureGenerator.java.txt "$fixture_tmp/LegacyFixtureGenerator.java"

"$jdk8_bin/keytool" -genkeypair -alias ddd4j-legacy -keyalg DSA -keysize 1024 \
  -validity 36500 \
  -dname 'CN=ddd4j-legacy-fixture,OU=Test,O=ddd4j,L=Shanghai,ST=Shanghai,C=CN' \
  -keystore "$fixture_tmp/legacy-private.jks" -storetype JKS \
  -storepass ddd4j-legacy-store-password -keypass ddd4j-legacy-key-password -noprompt

"$jdk8_bin/keytool" -exportcert -alias ddd4j-legacy \
  -keystore "$fixture_tmp/legacy-private.jks" -storepass ddd4j-legacy-store-password \
  -file "$fixture_tmp/legacy-public.cer"

"$jdk8_bin/keytool" -importcert -alias ddd4j-legacy \
  -keystore "$fixture_tmp/legacy-public.jks" -storetype JKS \
  -storepass ddd4j-legacy-store-password -file "$fixture_tmp/legacy-public.cer" -noprompt

legacy_cp="$HOME/.m2/repository/de/schlichtherle/truelicense/truelicense-core/1.33/truelicense-core-1.33.jar:$HOME/.m2/repository/de/schlichtherle/truelicense/truelicense-xml/1.33/truelicense-xml-1.33.jar:$HOME/.m2/repository/commons-codec/commons-codec/1.6/commons-codec-1.6.jar"
mkdir -p "$fixture_tmp/classes"
"$jdk8_bin/javac" -cp "$legacy_cp" -d "$fixture_tmp/classes" "$fixture_tmp/LegacyFixtureGenerator.java"
"$jdk8_bin/java" -cp "$fixture_tmp/classes:$legacy_cp" LegacyFixtureGenerator \
  "$fixture_tmp/legacy-private.jks" "$fixture_tmp/legacy-v1.lic" \
  ddd4j-legacy-store-password ddd4j-legacy-key-password ddd4j-legacy ddd4j-legacy-fixture
```

预期固定夹具 SHA-256：

- `legacy-public.jks`: `87cd0f61a5e02652f8bed76cc728ad6ee6cc902c4597594c229844ddfbc4318e`
- `legacy-v1.lic`: `1d247eb6af7ca5fd4a8b76c97d21abb7be462e10079c17d5aefbc71a9007d734`

重新执行会生成新的随机密钥和不同哈希；当前验证没有重生成或替换已验收的二进制夹具。
