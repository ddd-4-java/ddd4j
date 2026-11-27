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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 仅供 PostgreSQL 事务原子性集成测试使用的 Outbox 实体。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tx_test_outbox")
public class TransactionOutboxEntity {

    /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
    @Id
    private String id;

    /** \u4f9b\u6301\u4e45\u5316\u6216\u5e8f\u5217\u5316\u5f80\u8fd4\u6bd4\u8f83\u7684\u5b57\u6bb5\u503c\u3002 */
    @Column(name = "payload_value")
    private String value;

    /**
     * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>TransactionOutboxEntity</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
     * @param id \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e
     * @param value \u4f9b\u6301\u4e45\u5316\u6216\u5e8f\u5217\u5316\u5f80\u8fd4\u6bd4\u8f83\u7684\u5b57\u6bb5\u503c
     */
    public TransactionOutboxEntity(String id, String value) {
        this.id = id;
        this.value = value;
    }
}
