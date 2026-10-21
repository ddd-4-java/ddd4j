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
package io.ddd4j.extension.excel.fill;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/** 三线共同的模板流重读契约。 */
class ExcelFillerTemplateStreamContractTest {

    @Test
    void byteArrayStreamIsReusedAndOtherStreamsAreBufferedWithoutClosingSource() throws Exception {
        Method convert = ExcelFiller.class.getDeclaredMethod("toNonClosingStream", InputStream.class);
        convert.setAccessible(true);
        byte[] template = "template-data".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream direct = new ByteArrayInputStream(template);

        assertSame(direct, convert.invoke(null, direct));

        final boolean[] closed = {false};
        InputStream source = new FilterInputStream(new ByteArrayInputStream(template)) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        InputStream buffered = (InputStream) convert.invoke(null, source);

        assertNotSame(source, buffered);
        assertFalse(closed[0]);
        assertArrayEquals(template, read(buffered));
        buffered.reset();
        assertArrayEquals(template, read(buffered));
    }

    private static byte[] read(InputStream stream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] chunk = new byte[4];
        int count;
        while ((count = stream.read(chunk)) != -1) {
            output.write(chunk, 0, count);
        }
        return output.toByteArray();
    }
}
