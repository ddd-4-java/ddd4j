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
package io.ddd4j.extension.qrcode.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Getter;

/** Ordered, non-atomic batch result. */
@Getter
public final class QrCodeBatchResult {

    private final List<QrCodeBatchItemResult> items;

    public QrCodeBatchResult(List<QrCodeBatchItemResult> items) {
        this.items = Collections.unmodifiableList(new ArrayList<QrCodeBatchItemResult>(items));
    }

    public long successCount() {
        return items.stream().filter(QrCodeBatchItemResult::isSuccess).count();
    }

    public long failureCount() {
        return items.size() - successCount();
    }
}
