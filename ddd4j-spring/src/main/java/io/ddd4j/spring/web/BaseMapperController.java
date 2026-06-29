/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * <p><b>迁移说明</b>：自 2.0.x 起，本类将从 {@code ddd4j-spring} 下移到
 * {@code ddd4j-boot-web-core}（Spring Boot starter）。新业务请直接依赖 {@code ddd4j-boot-web-core}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @deprecated 自 2.0.x 起下移到 {@code ddd4j-boot-web-core.BaseMapperController}
 */
package io.ddd4j.spring.web;

import com.github.dozermapper.core.Mapper;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

@Deprecated
public abstract class BaseMapperController extends BaseController {

    @Autowired(required = false)
    @Getter
    private Mapper beanMapper;

}
