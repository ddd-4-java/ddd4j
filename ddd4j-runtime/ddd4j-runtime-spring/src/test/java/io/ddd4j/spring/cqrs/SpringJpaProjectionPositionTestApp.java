/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.spring.cqrs;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** \u4e3a Spring JPA \u6295\u5f71\u68c0\u67e5\u70b9\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u7684\u542f\u52a8\u914d\u7f6e\u3002 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = SpringJpaProjectionPosition.class)
@EnableJpaRepositories(basePackageClasses = SpringJpaProjectionPositionDataRepository.class)
@Import(SpringJpaProjectionPositionRepository.class)
class SpringJpaProjectionPositionTestApp {
}
