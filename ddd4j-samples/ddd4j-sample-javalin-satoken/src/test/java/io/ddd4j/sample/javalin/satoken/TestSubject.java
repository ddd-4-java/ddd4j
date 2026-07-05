package io.ddd4j.sample.javalin.satoken;

import io.ddd4j.auth.satoken.subject.SaTokenSubject;

/**
 * 测试用 Subject：覆盖 {@link #getUserId()}，从 principal 取值而非走 sa-token extra 通道。
 *
 * <p>业务代码（{@code AuthController}）调用 {@code SubjectKit.getUserId()}，sa-token 默认实现
 * 走 {@code StpKit.getUserId()} → {@code StpUtil.getExtra("uid")}，未集成 sa-token-jwt 时抛
 * {@code ApiDisabledException}。本测试侧通过覆盖 getUserId 回退到 ddd4j 默认从
 * {@link io.ddd4j.core.auth.AuthPrincipal} 读取的方式，无需修改任何业务代码。
 */
public class TestSubject extends SaTokenSubject {

    @Override
    public Object getUserId() {
        // 回退到 Subject 接口默认实现：从 principal.getUserId() 取值
        var principal = getPrincipal();
        return principal != null ? principal.getUserId() : null;
    }
}