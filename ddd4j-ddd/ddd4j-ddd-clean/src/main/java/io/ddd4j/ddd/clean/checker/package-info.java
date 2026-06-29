/**
 * ddd4j-ddd-clean：Clean Architecture 目录规范检查。
 * <p>
 * 确保项目启动时符合 Clean Architecture 的分层约束：
 * <ul>
 *   <li>domain 层不依赖任何外层（零 Spring/MyBatis/Web 依赖）</li>
 *   <li>application 层只依赖 domain</li>
 *   <li>adapter 层实现 domain 定义的端口（Port）</li>
 *   <li>infrastructure 层提供框架配置</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
package io.ddd4j.ddd.clean.checker;
