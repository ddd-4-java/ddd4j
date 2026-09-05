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

class OnsAcknowledgmentContractTest {

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
