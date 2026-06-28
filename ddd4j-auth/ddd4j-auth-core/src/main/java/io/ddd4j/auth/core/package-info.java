/**
 * ddd4j 认证/授权核心契约聚合模块。
 *
 * <p>本模块依赖 {@code ddd4j-core}，re-export 以下契约（均在 {@code io.ddd4j.core.subject} 包）：
 * <ul>
 *   <li>{@link io.ddd4j.core.subject.Subject} - 核心契约（读取 + 校验 + 会话操作）</li>
 *   <li>{@link io.ddd4j.core.subject.SubjectKit} - 静态门面 + 全局注册中心</li>
 *   <li>{@link io.ddd4j.core.subject.SubjectProvider} - Subject 工厂 SPI</li>
 *   <li>{@link io.ddd4j.core.subject.SubjectDataProvider} - 权限数据源 SPI</li>
 *   <li>{@link io.ddd4j.core.subject.SubjectStrategy} - 核心行为策略集</li>
 *   <li>{@link io.ddd4j.core.subject.AuthRequest} - 登录请求载体</li>
 *   <li>{@link io.ddd4j.core.subject.AuthPrincipal} - 认证主体值对象</li>
 * </ul>
 *
 * <p>设计说明：Subject 契约保留在 {@code ddd4j-core}（因 {@code ThreadContext} 等核心类已依赖），
 * 本模块作为 auth 聚合层的统一入口，所有 {@code auth-*} 子模块统一依赖本模块而非直接依赖 {@code ddd4j-core}，
 * 避免循环依赖的同时形成清晰的模块拓扑。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
package io.ddd4j.auth.core;
