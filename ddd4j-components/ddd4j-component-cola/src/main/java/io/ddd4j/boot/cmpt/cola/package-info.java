/**
 * COLA 架构组件集成。
 *
 * <p>提供 ddd4j 与 Alibaba COLA 组件的集成：
 * <ul>
 *   <li>{@link io.ddd4j.boot.cmpt.cola.config.ColaAutoConfiguration} — COLA 自动配置（聚合 domain/extension/catchlog/statemachine）</li>
 *   <li>{@link io.ddd4j.boot.cmpt.cola.exception.ColaExceptionHandler} — COLA 异常到 {@code ApiRestResponse} 转换</li>
 *   <li>{@link io.ddd4j.boot.cmpt.cola.handler.Ddd4jResponseHandler} — catchlog 的 ResponseHandler 扩展</li>
 * </ul>
 *
 * <p>COLA 组件（cola-component-*）自带 Spring Boot AutoConfiguration，
 * 本模块做的是 ddd4j 风格的适配（统一响应、异常处理）。
 *
 * @author wandl
 * @since 3.4.x
 */
package io.ddd4j.boot.cmpt.cola;
