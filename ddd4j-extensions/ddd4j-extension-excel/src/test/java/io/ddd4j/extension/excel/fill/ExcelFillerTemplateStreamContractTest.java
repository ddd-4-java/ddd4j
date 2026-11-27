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
     * 通过 <code>output.toByteArray()</code> 取得当前测试需要的结果。
     *
     * @param stream 传给 <code>stream.read()</code> 的 <code>InputStream</code> 输入
     * @return 当前测试使用的结果，取自 <code>output.toByteArray()</code>
     * @throws Exception 准备或读取夹具文件时失败
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
     * 验证<code>convert.invoke(null, direct)</code> 与…为同一实例 <code>direct</code>，<code>buffered</code> 与…为不同的实例 <code>source</code>，<code>read(buffered)</code> 与…的元素逐项相同 <code>template</code>。
     *
     * @throws Exception 准备或读取夹具文件、反射访问被测成员时失败
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
