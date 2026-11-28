/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.spring.cqrs;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data 为投影位置实体生成的 JPA 仓库。
 */
public interface SpringJpaProjectionPositionDataRepository
        extends JpaRepository<SpringJpaProjectionPosition, String> {
}
