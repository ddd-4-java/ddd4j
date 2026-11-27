package io.ddd4j.data.mybatis.repository;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 编译期拒绝聚合 ID 和仓储 ID 不匹配，保留合法组合。 */
class MybatisRepositoryIdTypeTest {
    /** JUnit \u4e3a\u672c\u6b21\u6d4b\u8bd5\u5206\u914d\u7684\u4e34\u65f6\u76ee\u5f55\u3002 */
    @TempDir
    Path directory;

    /**
     * \u9a8c\u8bc1<code>compiles(baseClass, "Long")</code>\u4e3a\u5047\u3002
     * @param baseClass \u4f20\u7ed9 <code>assertThat()</code> \u7684 <code>String</code> \u8f93\u5165
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @ParameterizedTest
    @ValueSource(strings = {"MybatisAggregateRepository", "BaseRepositoryImpl"})
    void rejectsMismatchedIdentityType(String baseClass) throws Exception {
        assertThat(compiles(baseClass, "Long")).isFalse();
    }

    /**
     * \u9a8c\u8bc1<code>compiles(baseClass, "String")</code>\u4e3a\u771f\u3002
     * @param baseClass \u4f20\u7ed9 <code>assertThat()</code> \u7684 <code>String</code> \u8f93\u5165
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @ParameterizedTest
    @ValueSource(strings = {"MybatisAggregateRepository", "BaseRepositoryImpl"})
    void acceptsMatchingIdentityType(String baseClass) throws Exception {
        assertThat(compiles(baseClass, "String")).isTrue();
    }

    /**
     * \u8c03\u7528 <code>directory.resolve()</code>\u3001<code>Files.writeString()</code>\u3001<code>ToolProvider.getSystemJavaCompiler()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param baseClass \u4f20\u7ed9 <code>Files.writeString()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param idType \u4f20\u7ed9 <code>Files.writeString()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u64cd\u4f5c\u662f\u5426\u6ee1\u8db3\u5224\u5b9a\u6761\u4ef6
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    private boolean compiles(String baseClass, String idType) throws Exception {
        Path source = directory.resolve("IdentityProbe.java");
        Files.writeString(source, """
                import com.baomidou.mybatisplus.core.mapper.BaseMapper;
                import io.ddd4j.core.cqrs.query.Query;
                import io.ddd4j.core.ddd.model.AggregateRoot;
                import io.ddd4j.data.mybatis.repository.MybatisAggregateRepository;
                import io.ddd4j.data.mybatis.repository.impl.BaseRepositoryImpl;
                class IdentityProbe {
                    static class Model extends AggregateRoot<String> {
                        public String id() { return "id"; }
                    }
                    static class Po { }
                    interface Mapper extends BaseMapper<Po> { }
                    static class ModelQuery extends Query<Model> { }
                    static class Repo extends %s<Mapper, Model, Po, ModelQuery, %s> { }
                }
                """.formatted(baseClass, idType));
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var files = compiler.getStandardFileManager(null, null, null)) {
            return compiler.getTask(null, files, diagnostic -> { },
                    List.of("-proc:none", "-classpath", System.getProperty("java.class.path"),
                            "-d", directory.toString()), null,
                    files.getJavaFileObjects(source.toFile())).call();
        }
    }
}
