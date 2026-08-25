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
package io.ddd4j.kit.lang;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * 拼音工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### BASE-KIT : PinyinKit ###")
@UtilityClass
public class PinyinKit {
    HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();

    static {
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    public String toPinyin(String hanyu) {
        try {
            return PinyinHelper.toHanYuPinyinString(hanyu, format, "", true);
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            log.error("转换拼音失败", e);
            return hanyu;
        }
    }


    public String toPINYIN(String hanyu) {
        try {
            return PinyinHelper.toHanYuPinyinString(hanyu, format, "", true).toUpperCase();
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            log.error("转换拼音失败", e);
            return hanyu;
        }
    }
}
