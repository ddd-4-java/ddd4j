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
package io.ddd4j.data.event.store.jpa.fixture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 仅供 PostgreSQL 事务原子性集成测试使用的 Outbox 实体。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tx_test_outbox")
public class TransactionOutboxEntity {

    /**
     * 用于构造和定位测试记录的主键。
     */
    @Id
    private String id;

    /**
     * 供持久化或序列化往返比较的字段值。
     */
    @Column(name = "payload_value")
    private String value;

    /**
     * 以给定状态构造 <code>TransactionOutboxEntity</code> 测试对象。
     *
     * @param id    用于构造和定位测试记录的主键
     * @param value 供持久化或序列化往返比较的字段值
     */
    public TransactionOutboxEntity(String id, String value) {
        this.id = id;
        this.value = value;
    }
}
