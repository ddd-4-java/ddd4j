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
package io.ddd4j.extension.monitor.message;

import lombok.Data;

import java.util.List;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * 通知（@）配置。原名称为 {@code AtVO}。
 *
 * <p>传入机器人消息体时使用，控制消息是否 @ 一些用户或 @ 全体。
 */
@Data
public class At {

    /**
     * 被 @ 的手机号列表
     */
    private List<String> atMobiles;
    /**
     * 是否 @ 所有人
     */
    private Boolean isAtAll = false;

    /**
     * \u521b\u5efa\u4e00\u4e2a\u7a7a @\uff08\u4e0d @ \u4efb\u4f55\u7528\u6237\uff09\u3002
     *
     * @return \u7a7a\u901a\u77e5\u914d\u7f6e
     */
    public static At none() {
        return new At();
    }

    /**
     * \u521b\u5efa\u4e00\u4e2a @ \u5168\u4f53\u3002
     *
     * @return \u5168\u4f53\u901a\u77e5\u914d\u7f6e
     */
    public static At all() {
        At at = new At();
        at.setIsAtAll(true);
        return at;
    }

    /**
     * \u521b\u5efa\u4e00\u4e2a\u9488\u5bf9\u6307\u5b9a\u624b\u673a\u53f7\u5217\u8868\u7684 @\u3002
     * @param mobiles \u624b\u673a\u53f7\u5217\u8868
     *
     * @return \u9488\u5bf9\u624b\u673a\u53f7\u5217\u8868\u7684\u901a\u77e5\u914d\u7f6e
     */
    public static At ofMobiles(List<String> mobiles) {
        At at = new At();
        at.setAtMobiles(mobiles);
        return at;
    }
}
