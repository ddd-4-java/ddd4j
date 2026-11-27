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
package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 通过 <code>mock()</code>、<code>message.getMsgID()</code>、<code>acknowledgment.nack()</code> 验证 <code>OnsAcknowledgmentContract</code> 的运行行为。
 */
class OnsAcknowledgmentContractTest {

    /**
     * 验证<code>acknowledgment.action()</code> 等于 <code>com.aliyun.openservices.ons.api.Action.ReconsumeLater</code>，<code>acknowledgment.isAcknowledged()</code>为真。
     */
    @Test
    void shouldMapNackToProtocolRetryAction() {
        Message message = mock(Message.class);
        when(message.getMsgID()).thenReturn("stable-id");
        OnsAcknowledgment acknowledgment = new OnsAcknowledgment(mock(ConsumeContext.class), message);

        acknowledgment.nack(true);

        assertEquals(com.aliyun.openservices.ons.api.Action.ReconsumeLater, acknowledgment.action());
        assertTrue(acknowledgment.isAcknowledged());
    }
}
