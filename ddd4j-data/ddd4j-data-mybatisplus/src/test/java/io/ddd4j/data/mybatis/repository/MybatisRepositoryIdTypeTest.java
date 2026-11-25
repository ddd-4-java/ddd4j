package io.ddd4j.data.mybatis.repository;

import java.nio.charset.StandardCharsets;

import javax.tools.StandardJavaFileManager;

import javax.tools.JavaCompiler;

import java.util.Arrays;

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
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"MybatisAggregateRepository", "BaseRepositoryImpl"})
    void rejectsMismatchedIdentityType(String baseClass) throws Exception {
        assertThat(compiles(baseClass, "Long")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MybatisAggregateRepository", "BaseRepositoryImpl"})
    void acceptsMatchingIdentityType(String baseClass) throws Exception {
        assertThat(compiles(baseClass, "String")).isTrue();
    }

    private boolean compiles(String baseClass, String idType) throws Exception {
        Path source = directory.resolve("IdentityProbe.java");
        Files.write(source, String.format("import com.baomidou.mybatisplus.core.mapper.BaseMapper;\nimport io.ddd4j.core.cqrs.query.Query;\nimport io.ddd4j.core.ddd.model.AggregateRoot;\nimport io.ddd4j.data.mybatis.repository.MybatisAggregateRepository;\nimport io.ddd4j.data.mybatis.repository.impl.BaseRepositoryImpl;\nclass IdentityProbe {\n    static class Model extends AggregateRoot<String> {\n        public String id() { return \"id\"; }\n    }\n    static class Po { }\n    interface Mapper extends BaseMapper<Po> { }\n    static class ModelQuery extends Query<Model> { }\n    static class Repo extends %s<Mapper, Model, Po, ModelQuery, %s> { }\n}", baseClass, idType).getBytes(StandardCharsets.UTF_8));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
            return compiler.getTask(null, files, diagnostic -> { },
                    Arrays.asList("-proc:none", "-classpath", System.getProperty("java.class.path"),
                            "-d", directory.toString()), null,
                    files.getJavaFileObjects(source.toFile())).call();
        }
    }
}
