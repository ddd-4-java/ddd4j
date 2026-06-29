package io.ddd4j.extension.pf4j.annotation;

import java.lang.annotation.*;

/**
 * 扩展点注解：用于标注某个功能扩展点的信息
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
public @interface ExtensionMapping {

    public String id() default "";

    public String title() default "";

    public String ver() default "1.0.0";

    public String desc() default "";

}
