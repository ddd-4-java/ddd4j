/**
 * ddd4j-ddd-rules-cola：COLA 架构目录规范检查。
 * <p>
 * COLA（Clean Object-oriented and Layered Architecture）是阿里巴巴推荐的 DDD 落地架构。
 * 本模块确保项目启动时符合 COLA 的分层约束：
 * <ul>
 *   <li>domain 层不依赖任何外层</li>
 *   <li>adapter 层实现 domain 定义的 gateway 接口</li>
 *   <li>application 层包含 executor（命令）或 query（查询）子包</li>
 *   <li>infrastructure 层提供框架配置和外部服务调用</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
package io.ddd4j.ddd.cola.checker;
