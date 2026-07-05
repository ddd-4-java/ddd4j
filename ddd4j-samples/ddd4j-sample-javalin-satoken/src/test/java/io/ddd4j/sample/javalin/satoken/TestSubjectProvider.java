package io.ddd4j.sample.javalin.satoken;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

/**
 * 测试用 SubjectProvider：返回 {@link TestSubject} 实例。
 */
public class TestSubjectProvider implements SubjectProvider {

    @Override
    public Subject getSubject() {
        return new TestSubject();
    }

    @Override
    public Subject getSubject(String realm) {
        return new TestSubject();
    }
}