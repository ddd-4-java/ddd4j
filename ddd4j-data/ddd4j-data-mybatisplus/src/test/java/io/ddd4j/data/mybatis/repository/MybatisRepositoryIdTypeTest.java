package io.ddd4j.data.mybatis.repository;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 编译期拒绝聚合 ID 和仓储 ID 不匹配，保留合法组合。
 */
class MybatisRepositoryIdTypeTest {
    /**
     * JUnit 为本次测试分配的临时目录。
     */
    @TempDir
    Path directory;

    /**
     * 验证<code>compiles(baseClass, "Long")</code>为假。
     *
     * @param baseClass 传给 <code>assertThat()</code> 的 <code>String</code> 输入
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @ParameterizedTest
    @ValueSource(strings = {"MybatisAggregateRepository", "BaseRepositoryImpl"})
    void rejectsMismatchedIdentityType(String baseClass) throws Exception {
        assertThat(compiles(baseClass, "Long")).isFalse();
    }

    /**
     * 验证<code>compiles(baseClass, "String")</code>为真。
     *
     * @param baseClass 传给 <code>assertThat()</code> 的 <code>String</code> 输入
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @ParameterizedTest
    @ValueSource(strings = {"MybatisAggregateRepository", "BaseRepositoryImpl"})
    void acceptsMatchingIdentityType(String baseClass) throws Exception {
        assertThat(compiles(baseClass, "String")).isTrue();
    }

    /**
     * 调用 <code>directory.resolve()</code>、<code>Files.writeString()</code>、<code>ToolProvider.getSystemJavaCompiler()</code> 执行当前夹具操作。
     *
     * @param baseClass 传给 <code>Files.writeString()</code> 的 <code>String</code> 输入
     * @param idType    传给 <code>Files.writeString()</code> 的 <code>String</code> 输入
     * @return 当前测试操作是否满足判定条件
     * @throws Exception 准备或读取夹具文件时失败
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
            return compiler.getTask(null, files, diagnostic -> {
                    },
                    List.of("-proc:none", "-classpath", System.getProperty("java.class.path"),
                            "-d", directory.toString()), null,
                    files.getJavaFileObjects(source.toFile())).call();
        }
    }
}
