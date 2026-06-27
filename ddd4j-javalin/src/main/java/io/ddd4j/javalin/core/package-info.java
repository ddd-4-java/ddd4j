/**
 * ddd4j-javalin-core：Javalin 框架核心 Guice 桥接。
 * <p>
 * 本模块提供 3 个核心 SPI 的 Javalin/Guice 实现：DomainEventPublisher（Guava EventBus）、
 * SubjectProvider（Guice Injector）、I18nProvider（ResourceBundle）。
 * 用户通过 {@code Ddd4jJavalinModule} 一行启用全部 SPI。
 *
 * @since 3.4.x
 */
package io.ddd4j.javalin.core;
