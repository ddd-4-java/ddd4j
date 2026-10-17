/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.sample.javalin.satoken.rbac.infrastructure;

import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Role;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 三线内存仓储查询列表的快照与可修改契约。 */
class InMemoryRepositoryListContractTest {

    @Test
    void roleQueriesReturnDetachedMutableLists() {
        InMemoryRoleRepository repository = new InMemoryRoleRepository();
        repository.save(new Role("role-1", "operator", "Operator", "", Role.Status.ENABLED));

        List<Role> result = repository.findAll();
        result.clear();

        assertEquals(1, repository.findAll().size());
    }

    @Test
    void userQueriesReturnDetachedMutableLists() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        repository.save(new User("user-1", "alice", "secret", "Alice", User.Status.ENABLED));

        List<User> all = repository.findAll();
        List<User> enabled = repository.findByStatus(User.Status.ENABLED);
        all.clear();
        enabled.clear();

        assertEquals(1, repository.findAll().size());
        assertEquals(1, repository.findByStatus(User.Status.ENABLED).size());
    }
}
