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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三线共同的模板流重读契约。
 */
class ExcelFillerTemplateStreamContractTest {

    /**
     * \u901a\u8fc7 <code>output.toByteArray()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param stream \u4f20\u7ed9 <code>stream.read()</code> \u7684 <code>InputStream</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>output.toByteArray()</code>
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    private static byte[] read(InputStream stream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] chunk = new byte[4];
        int count;
        while ((count = stream.read(chunk)) != -1) {
            output.write(chunk, 0, count);
        }
        return output.toByteArray();
    }

    /**
     * \u9a8c\u8bc1<code>convert.invoke(null, direct)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>direct</code>\uff0c<code>buffered</code> \u4e0e\u2026\u4e3a\u4e0d\u540c\u7684\u5b9e\u4f8b <code>source</code>\uff0c<code>read(buffered)</code> \u4e0e\u2026\u7684\u5143\u7d20\u9010\u9879\u76f8\u540c <code>template</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u3001\u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
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
}
