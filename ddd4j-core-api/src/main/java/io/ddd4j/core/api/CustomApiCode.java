package io.ddd4j.core.api;

/**
 * 自定义 API 码接口（纯 Java，无框架依赖）
 *
 * @author wandl
 */
public interface CustomApiCode {

    int getCode();

    String getReason();

    default String getStatus() {
        return Constants.RT_SUCCESS;
    }

}
