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

public abstract class BaseMapperController extends BaseController {

    @Autowired(required = false)
    @Getter
    private Mapper beanMapper;

}
