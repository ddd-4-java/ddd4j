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
package io.ddd4j.annotation;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 日志操作类型-枚举
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public enum BusinessType {

    /**
     * 用户登录
     */
    LOGIN("login", "用户登录"),
    /**
     * 用户注销
     */
    LOGOUT("logout", "用户注销"),
    /**
     * 数据写入
     */
    INSERT("insert", "数据写入"),
    /**
     * 数据刪除
     */
    DELETE("delete", "数据刪除"),
    /**
     * 数据更新
     */
    UPDATE("update", "数据更新作"),
    /**
     * 数据查询
     */
    SELECT("select", "数据查询"),
    /**
     * 文件上传
     */
    UPLOAD("upload", "文件上传"),
    /**
     * 文件下载
     */
    DOWNLOAD("download", "文件下载"),
    /**
     * 发送邮件
     */
    EMAIL("email", "发送邮件"),
    /**
     * 发送短信
     */
    SMS("sms", "发送短信");

    /**
     * 业务类型标识键
     */
    private String key;
    /**
     * 业务类型描述
     */
    private String desc;

    /**
     * 构造业务类型枚举
     *
     * @param key  业务类型标识键
     * @param desc 业务类型描述
     */
    BusinessType(String key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /**
     * 将所有业务类型转换为 Map 列表
     *
     * @return 包含所有业务类型键值对的列表
     */
    public static List<Map<String, String>> toList() {
        List<Map<String, String>> typeList = new LinkedList<Map<String, String>>();
        for (BusinessType typeEnum : BusinessType.values()) {
            typeList.add(typeEnum.toMap());
        }
        return typeList;
    }

    /**
     * 获取业务类型标识键
     *
     * @return 业务类型键
     */
    public String getKey() {
        return key;
    }

    /**
     * 设置业务类型标识键
     *
     * @param key 业务类型键
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * 获取业务类型描述
     *
     * @return 业务类型描述
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 设置业务类型描述
     *
     * @param desc 业务类型描述
     */
    public void setDesc(String desc) {
        this.desc = desc;
    }

    /**
     * 比较当前业务类型是否与指定类型相同
     *
     * @param relation 待比较的业务类型
     * @return 若相同返回 true，否则返回 false
     */
    public boolean equals(BusinessType relation) {
        return this.compareTo(relation) == 0;
    }

    /**
     * 将当前业务类型转换为包含键值对的 Map
     *
     * @return 包含 "key" 和 "desc" 的 Map
     */
    public Map<String, String> toMap() {
        Map<String, String> typeMap = new HashMap<String, String>();
        typeMap.put("key", this.getKey());
        typeMap.put("desc", this.getDesc());
        return typeMap;
    }

}
