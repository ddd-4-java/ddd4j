/**
 * ddd4j-adapter-guice：Guice 桥接。
 * <p>
 * 本模块提供 3 个核心 SPI 的 Guice 实现：DomainEventPublisher（Guava EventBus）、
 * SubjectProvider（Guice Injector）、I18nProvider（ResourceBundle）。
 * 用户通过 {@code Ddd4jGuiceModule} 一行启用全部 SPI。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
package io.ddd4j.guice;
