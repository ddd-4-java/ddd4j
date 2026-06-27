/**
 * ddd4j-auth-core：纯 Java 认证/授权抽象。
 * <p>
 * 本模块零 Spring 依赖，仅提供 Subject/AuthenticationException/BaseAuth SPI 与认证注解。
 * 各框架实现（ddd4j-auth-satoken、ddd4j-auth-security、ddd4j-auth-shiro）通过 SubjectProvider SPI 注入。
 *
 * @since 3.4.x
 */
package io.ddd4j.auth;
