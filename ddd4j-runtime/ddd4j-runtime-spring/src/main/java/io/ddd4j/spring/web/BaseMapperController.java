/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.spring.web;

import com.github.dozermapper.core.Mapper;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 带对象映射能力的 MVC 基础控制器。
 * <p>
 * 在 {@link BaseController} 基础上增加了 Dozer {@link Mapper} 注入，
 * 用于在 Controller 层进行对象间的属性拷贝与转换。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public abstract class BaseMapperController extends BaseController {

    /** Dozer Bean 映射器 */
    @Autowired(required = false)
    @Getter
    private Mapper beanMapper;

}
