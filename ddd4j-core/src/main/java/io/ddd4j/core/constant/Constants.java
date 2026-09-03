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
package io.ddd4j.core.constant;

/**
 * 框架核心常量定义。
 * <p>
 * 包含请求结果状态标识（success/fail/error）、主题参数名称、语言参数名称、
 * 以及业务日志 Marker 名称等全局常量。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Constants {

    /**
     * 请求结果状态：成功
     */
    public static final String RT_SUCCESS = "success";
    /**
     * 请求结果状态：失败
     */
    public static final String RT_FAIL = "fail";
    /**
     * 请求结果状态：错误
     */
    public static final String RT_ERROR = "error";
    /**
     * 主题参数名称
     */
    public static final String THEME_PARAM_NAME = "theme";
    /**
     * 主题参数默认值
     */
    public static final String THEME_PARAM_DEFAULT = "default";
    /**
     * 主题资源类路径
     */
    public static final String THEME_SOURCE_CLASSPATH = "classpath:/static/assets/css/themes/";
    /**
     * 语言参数名称
     */
    public static final String LANG_PARAM_NAME = "lang";
    /**
     * 访问日志 Marker 名称。
     *
     * <p>2.0.x 破坏性变更（ADR-0002 迁移义务①）：由 SLF4J {@code Marker} 改为纯 {@code String}，
     * ddd4j-core 不再依赖日志门面，日志能力由家族模块（ddd4j-kit）与适配层承担。
     */
    public static final String ACCESS_MARKER = "io.hiwepy.access";
    /**
     * 授权日志 Marker 名称（2.0.x 破坏性变更：Marker→String，见 ADR-0002 迁移义务①）。
     */
    public static final String AUTHZ_MARKER = "io.hiwepy.authz";
    /**
     * 业务日志 Marker 名称（2.0.x 破坏性变更：Marker→String，见 ADR-0002 迁移义务①）。
     */
    public static final String BIZ_MARKER = "io.hiwepy.biz";

}

