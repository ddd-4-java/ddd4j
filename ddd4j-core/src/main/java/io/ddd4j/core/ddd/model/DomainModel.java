/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.core.ddd.model;

import java.io.Serializable;

/**
 * Domain model marker for the tactical DDD path.
 *
 * <p>This contract is intentionally persistence-agnostic. Domain models must not
 * depend on MyBatis, JPA, Spring, or any runtime framework. Persistence objects
 * such as PO/Entity classes belong to infrastructure adapters.</p>
 *
 * @param <ID> domain identity type
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface DomainModel<ID extends Serializable> extends Serializable {

    /**
     * Returns the domain identity.
     *
     * @return domain identity
     */
    ID id();
}
