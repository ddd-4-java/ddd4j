/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.core.constant;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * 框架核心常量定义。
 * <p>
 * 包含请求结果状态标识（success/fail/error）、主题参数名称、语言参数名称、
 * 以及 SLF4J 业务日志 Marker 等全局常量。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Constants {

    /** 请求结果状态：成功 */
    public static final String RT_SUCCESS = "success";
    /** 请求结果状态：失败 */
    public static final String RT_FAIL = "fail";
    /** 请求结果状态：错误 */
    public static final String RT_ERROR = "error";
    /** 主题参数名称 */
    public static final String THEME_PARAM_NAME = "theme";
    /** 主题参数默认值 */
    public static final String THEME_PARAM_DEFAULT = "default";
    /** 主题资源类路径 */
    public static final String THEME_SOURCE_CLASSPATH = "classpath:/static/assets/css/themes/";
    /** 语言参数名称 */
    public static final String LANG_PARAM_NAME = "lang";
    /** 访问日志 Marker */
    public static Marker accessMarker = MarkerFactory.getMarker("io.hiwepy.access");
    /** 授权日志 Marker */
    public static Marker authzMarker = MarkerFactory.getMarker("io.hiwepy.authz");
    /** 业务日志 Marker */
    public static Marker bizMarker = MarkerFactory.getMarker("io.hiwepy.biz");

}

