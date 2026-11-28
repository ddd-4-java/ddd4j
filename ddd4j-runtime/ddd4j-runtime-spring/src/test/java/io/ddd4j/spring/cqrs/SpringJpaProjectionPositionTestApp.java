/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.spring.cqrs;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * 为 Spring JPA 投影检查点集成测试提供的启动配置。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = SpringJpaProjectionPosition.class)
@EnableJpaRepositories(basePackageClasses = SpringJpaProjectionPositionDataRepository.class)
@Import(SpringJpaProjectionPositionRepository.class)
class SpringJpaProjectionPositionTestApp {

    /**
     * 使用标准 JPA 接口创建测试代理，避免 Hibernate 6 与 Jakarta Persistence 3.2
     * 的 SchemaManager 返回类型冲突。
     *
     * @return 限定测试实体管理器工厂代理接口的后处理器
     */
    @Bean
    static BeanPostProcessor projectionEntityManagerFactoryInterface() {
        return new BeanPostProcessor() {
            /** 配置实体管理器工厂，其余 Bean 原样返回。 */
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof LocalContainerEntityManagerFactoryBean factory) {
                    factory.setEntityManagerFactoryInterface(EntityManagerFactory.class);
                }
                return bean;
            }
        };
    }
}
