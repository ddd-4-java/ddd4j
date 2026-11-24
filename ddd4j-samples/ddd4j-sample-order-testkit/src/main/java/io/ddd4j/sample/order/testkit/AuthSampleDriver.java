/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.order.testkit;

import java.util.Optional;

/**
 * Sa-Token/Shiro 的请求边界。平台保留原路由、凭据和令牌格式，适配器仅转换实际响应。
 * 每次请求必须完成原生拦截器和 finally 清理；禁止在适配器中替平台清理上下文。
 */
public interface AuthSampleDriver {
    /**
     * 调用实际登录入口。
     * @param username 已配置测试账号
     * @param password 已配置密码
     * @return 实际登录响应中的令牌
     */
    String login(String username, String password);
    /**
     * 调用实际需要认证的入口。
     * @param token 令牌
     * @return 实际受保护入口返回的主体；未登录/失效令牌抛 SecurityException
     */
    String authenticatedPrincipal(String token);
    /**
     * 调用实际需要授权的业务入口。
     * @param token 令牌
     * @param permission 权限
     * @return 实际受保护业务资源；拒绝抛 SecurityException
     */
    String authorizedResource(String token, String permission);
    /**
     * 调用实际退出入口。
     * @param token 令牌；实际退出入口必须使该令牌失效
     */
    void logout(String token);
    /**
     * 调用受保护的业务异常入口。
     * @param token 令牌；调用已注入业务异常的受保护入口并保留 IllegalStateException
     */
    void failingRequest(String token);
    /**
     * 探测原生请求上下文。
     * @return 请求结束后执行线程/原生作用域中的主体；直接探测上下文，不能固定返回空
     */
    Optional<String> contextPrincipal();
}
