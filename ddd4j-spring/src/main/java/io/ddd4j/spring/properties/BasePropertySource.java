package io.ddd4j.spring.properties;


import java.lang.annotation.*;

/**
 * 自定义资源文件读取，优先级最低
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BasePropertySource {

    /**
     * Indicate the resource location(s) of the properties file to be loaded.
     * for example, {@code "classpath:/com/example/app.yml"}
     *
     * @return location(s)
     */
    String value();

    /**
     * load app-{activeProfile}.yml
     *
     * @return {boolean}
     */
    boolean loadActiveProfile() default true;

    /**
     * Get the order value of this resource.
     *
     * @return order
     */
    int order() default Integer.MAX_VALUE;

}
