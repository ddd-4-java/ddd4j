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
package io.ddd4j.auth.license;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * 许可证测试专用的进程内 Preferences 工厂，避免接触操作系统用户配置。
 */
public final class LicenseTestPreferencesFactory implements PreferencesFactory {

    /**
     * 测试偏好工厂用于隔离系统偏好节点的根。
     */
    private static final InMemoryPreferences SYSTEM_ROOT = new InMemoryPreferences(null, "");
    /**
     * 测试偏好工厂隔离用户偏好节点的内存根。
     */
    private static final InMemoryPreferences USER_ROOT = new InMemoryPreferences(null, "");
    /**
     * 控制测试替身删除操作失败位置的模式。
     */
    private static volatile RemoveFailureMode removeFailureMode = RemoveFailureMode.NONE;

    /**
     * 通过 <code>Objects.equals()</code> 取得当前测试需要的结果。
     *
     * @param preferences 传给 <code>Objects.equals()</code> 的 <code>Preferences</code> 输入
     * @return 当前测试使用的结果，取自 <code>Objects.equals(USER_ROOT, preferences)</code>
     */
    static boolean isUserRoot(Preferences preferences) {
        return Objects.equals(USER_ROOT, preferences);
    }

    /**
     * 根据本次调用更新 <code>removeFailureMode = enabled ? RemoveFailureMode.RUNTIME : RemoveFailureMode.NONE</code>，用于后续测试步骤。
     *
     * @param enabled 是否开启当前测试使用的功能
     */
    static void setRemoveFailure(boolean enabled) {
        removeFailureMode = enabled ? RemoveFailureMode.RUNTIME : RemoveFailureMode.NONE;
    }

    /**
     * 根据本次调用更新 <code>removeFailureMode = Objects.requireNonNull(mode, "mode")</code>，用于后续测试步骤。
     *
     * @param mode 传给 <code>Objects.requireNonNull()</code> 的 <code>RemoveFailureMode</code> 输入
     */
    static void setRemoveFailureMode(RemoveFailureMode mode) {
        removeFailureMode = Objects.requireNonNull(mode, "mode");
    }

    /**
     * 根据本次调用更新 <code>removeFailureMode = RemoveFailureMode.NONE</code>，用于后续测试步骤。
     */
    static void reset() {
        removeFailureMode = RemoveFailureMode.NONE;
        SYSTEM_ROOT.clearAll();
        USER_ROOT.clearAll();
    }

    /**
     * 读取<code>SYSTEM_ROOT</code> 字段。
     *
     * @return <code>SYSTEM_ROOT</code> 字段
     */
    @Override
    public Preferences systemRoot() {
        return SYSTEM_ROOT;
    }

    /**
     * 读取<code>USER_ROOT</code> 字段。
     *
     * @return <code>USER_ROOT</code> 字段
     */
    @Override
    public Preferences userRoot() {
        return USER_ROOT;
    }

    /**
     * 将 <code>NONE</code>、<code>RUNTIME</code>、<code>ERROR</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    enum RemoveFailureMode {
        /**
         * 当前测试枚举用于覆盖分支的 <code>NONE</code> 值。
         */
        NONE,
        /**
         * 当前测试枚举用于覆盖分支的 <code>RUNTIME</code> 值。
         */
        RUNTIME,
        /**
         * 当前测试枚举用于覆盖分支的 <code>ERROR</code> 值。
         */
        ERROR
    }

    /**
     * 实现 <code>AbstractPreferences</code>，供测试调用 <code>putSpi()</code>、<code>getSpi()</code>、<code>removeSpi()</code> 并观察结果。
     */
    private static final class InMemoryPreferences extends AbstractPreferences {

        /**
         * 保存内存偏好节点或其他树结构中当前节点的子节点。
         */
        private final Map<String, InMemoryPreferences> children = new ConcurrentHashMap<>();
        /**
         * 当前测试记录的属性值或批量输入集合。
         */
        private final Map<String, String> values = new ConcurrentHashMap<>();

        /**
         * 使用 <code>super()</code> 初始化 <code>InMemoryPreferences</code> 测试夹具。
         *
         * @param parent 用于初始化 <code>InMemoryPreferences</code> 的 <code>AbstractPreferences</code> 值
         * @param name   当前测试指定的对象或属性名称
         */
        private InMemoryPreferences(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        /**
         * 调用 <code>values.put()</code> 执行当前夹具操作。
         *
         * @param key   用于在测试上下文或缓存中索引数据的键
         * @param value 当前用例传入的值
         */
        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        /**
         * 通过 <code>values.get()</code> 取得当前测试需要的结果。
         *
         * @param key 用于在测试上下文或缓存中索引数据的键
         * @return 当前测试使用的结果，取自 <code>values.get(key)</code>
         */
        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        /**
         * 调用 <code>values.remove()</code> 执行当前夹具操作。
         *
         * @param key 用于在测试上下文或缓存中索引数据的键
         */
        @Override
        protected void removeSpi(String key) {
            if (removeFailureMode == RemoveFailureMode.RUNTIME) {
                throw new IllegalStateException("synthetic Preferences remove failure");
            }
            if (removeFailureMode == RemoveFailureMode.ERROR) {
                throw new AssertionError("synthetic Preferences remove error");
            }
            values.remove(key);
        }

        /**
         * 调用 <code>values.clear()</code>、<code>children.clear()</code>、<code>parent()</code> 执行当前夹具操作。
         *
         * @throws BackingStoreException 尝试执行当前测试操作失败
         */
        @Override
        protected void removeNodeSpi() throws BackingStoreException {
            values.clear();
            children.clear();
            Preferences parent = parent();
            if (parent instanceof InMemoryPreferences) {
                ((InMemoryPreferences) parent).children.remove(name());
            }
        }

        /**
         * 通过 <code>values.keySet()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>values.keySet().toArray(new String[0])</code>
         * @throws BackingStoreException 尝试执行当前测试操作失败
         */
        @Override
        protected String[] keysSpi() throws BackingStoreException {
            return values.keySet().toArray(new String[0]);
        }

        /**
         * 通过 <code>children.keySet()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>children.keySet().toArray(new String[0])</code>
         * @throws BackingStoreException 尝试执行当前测试操作失败
         */
        @Override
        protected String[] childrenNamesSpi() throws BackingStoreException {
            return children.keySet().toArray(new String[0]);
        }

        /**
         * 按 <code>(Objects.nonNull(child))</code> 等实际条件选择当前用例返回的结果。
         *
         * @param name 当前测试指定的对象或属性名称
         * @return 根据测试条件返回的可能值，包括 <code>child</code>、<code>Objects.nonNull(existing) ? existing : candidate</code>
         */
        @Override
        protected AbstractPreferences childSpi(String name) {
            InMemoryPreferences child = children.get(name);
            if (Objects.nonNull(child)) {
                return child;
            }
            InMemoryPreferences candidate = new InMemoryPreferences(this, name);
            InMemoryPreferences existing = children.putIfAbsent(name, candidate);
            return Objects.nonNull(existing) ? existing : candidate;
        }

        /**
         * 当前实现只保存内存状态，内存偏好节点无需同步到外部存储。
         *
         * @throws BackingStoreException Preferences SPI 声明的外部存储异常
         */
        @Override
        protected void syncSpi() throws BackingStoreException {
            // 内存节点无需与外部存储同步。
        }

        /**
         * 当前实现只保存内存状态，内存偏好节点无需写入外部存储。
         *
         * @throws BackingStoreException Preferences SPI 声明的外部存储异常
         */
        @Override
        protected void flushSpi() throws BackingStoreException {
            // 内存节点无需刷新到外部存储。
        }

        /**
         * 调用 <code>children.values()</code>、<code>child.clearAll()</code>、<code>children.clear()</code> 执行当前夹具操作。
         */
        private void clearAll() {
            for (InMemoryPreferences child : children.values()) {
                child.clearAll();
            }
            children.clear();
            values.clear();
        }
    }
}
