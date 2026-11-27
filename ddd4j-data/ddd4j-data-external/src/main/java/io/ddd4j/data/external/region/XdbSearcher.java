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
package io.ddd4j.data.external.region;

/**
 * Region search fallback constants.
 */
public final class XdbSearcher {

    /** \u672a\u5339\u914d\u5730\u533a\u65f6\u7684\u539f\u59cb\u6570\u636e\u3002 */
    public static final String NOT_MATCH = "0|0|0|内网IP|内网IP";
    /** \u672a\u5339\u914d\u5730\u533a\u65f6\u7684\u7ed3\u6784\u5316\u5730\u5740\u3002 */
    public static final RegionAddress NOT_MATCH_REGION_ADDRESS = new RegionAddress(NOT_MATCH.split("\\|"));

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>XdbSearcher</code> \u5b9e\u4f8b\u3002 */
    private XdbSearcher() {
    }
}
