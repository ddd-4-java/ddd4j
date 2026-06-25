/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.web;

import com.github.dozermapper.core.Mapper;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseMapperController extends BaseController {

    @Autowired(required = false)
    @Getter
    private Mapper beanMapper;

}
