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

    /** \u6d4b\u8bd5\u504f\u597d\u5de5\u5382\u7528\u4e8e\u9694\u79bb\u7cfb\u7edf\u504f\u597d\u8282\u70b9\u7684\u6839\u3002 */
    private static final InMemoryPreferences SYSTEM_ROOT = new InMemoryPreferences(null, "");
    /** \u6d4b\u8bd5\u504f\u597d\u5de5\u5382\u9694\u79bb\u7528\u6237\u504f\u597d\u8282\u70b9\u7684\u5185\u5b58\u6839\u3002 */
    private static final InMemoryPreferences USER_ROOT = new InMemoryPreferences(null, "");
    /** \u63a7\u5236\u6d4b\u8bd5\u66ff\u8eab\u5220\u9664\u64cd\u4f5c\u5931\u8d25\u4f4d\u7f6e\u7684\u6a21\u5f0f\u3002 */
    private static volatile RemoveFailureMode removeFailureMode = RemoveFailureMode.NONE;

    /**
     * \u901a\u8fc7 <code>Objects.equals()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param preferences \u4f20\u7ed9 <code>Objects.equals()</code> \u7684 <code>Preferences</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Objects.equals(USER_ROOT, preferences)</code>
     */
    static boolean isUserRoot(Preferences preferences) {
        return Objects.equals(USER_ROOT, preferences);
    }

    /**
     * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>removeFailureMode = enabled ? RemoveFailureMode.RUNTIME : RemoveFailureMode.NONE</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
     * @param enabled \u662f\u5426\u5f00\u542f\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u529f\u80fd
     */
    static void setRemoveFailure(boolean enabled) {
        removeFailureMode = enabled ? RemoveFailureMode.RUNTIME : RemoveFailureMode.NONE;
    }

    /**
     * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>removeFailureMode = Objects.requireNonNull(mode, "mode")</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
     * @param mode \u4f20\u7ed9 <code>Objects.requireNonNull()</code> \u7684 <code>RemoveFailureMode</code> \u8f93\u5165
     */
    static void setRemoveFailureMode(RemoveFailureMode mode) {
        removeFailureMode = Objects.requireNonNull(mode, "mode");
    }

    /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>removeFailureMode = RemoveFailureMode.NONE</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
    static void reset() {
        removeFailureMode = RemoveFailureMode.NONE;
        SYSTEM_ROOT.clearAll();
        USER_ROOT.clearAll();
    }

    /**
     * \u8bfb\u53d6<code>SYSTEM_ROOT</code> \u5b57\u6bb5\u3002
     * @return <code>SYSTEM_ROOT</code> \u5b57\u6bb5
     */
    @Override
    public Preferences systemRoot() {
        return SYSTEM_ROOT;
    }

    /**
     * \u8bfb\u53d6<code>USER_ROOT</code> \u5b57\u6bb5\u3002
     * @return <code>USER_ROOT</code> \u5b57\u6bb5
     */
    @Override
    public Preferences userRoot() {
        return USER_ROOT;
    }

    /** \u5c06 <code>NONE</code>\u3001<code>RUNTIME</code>\u3001<code>ERROR</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    enum RemoveFailureMode {
        /** \u5f53\u524d\u6d4b\u8bd5\u679a\u4e3e\u7528\u4e8e\u8986\u76d6\u5206\u652f\u7684 <code>NONE</code> \u503c\u3002 */
        NONE,
        /** \u5f53\u524d\u6d4b\u8bd5\u679a\u4e3e\u7528\u4e8e\u8986\u76d6\u5206\u652f\u7684 <code>RUNTIME</code> \u503c\u3002 */
        RUNTIME,
        /** \u5f53\u524d\u6d4b\u8bd5\u679a\u4e3e\u7528\u4e8e\u8986\u76d6\u5206\u652f\u7684 <code>ERROR</code> \u503c\u3002 */
        ERROR
    }

    /** \u5b9e\u73b0 <code>AbstractPreferences</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>putSpi()</code>\u3001<code>getSpi()</code>\u3001<code>removeSpi()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class InMemoryPreferences extends AbstractPreferences {

        /** \u4fdd\u5b58\u5185\u5b58\u504f\u597d\u8282\u70b9\u6216\u5176\u4ed6\u6811\u7ed3\u6784\u4e2d\u5f53\u524d\u8282\u70b9\u7684\u5b50\u8282\u70b9\u3002 */
        private final Map<String, InMemoryPreferences> children = new ConcurrentHashMap<>();
        /** \u5f53\u524d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u5c5e\u6027\u503c\u6216\u6279\u91cf\u8f93\u5165\u96c6\u5408\u3002 */
        private final Map<String, String> values = new ConcurrentHashMap<>();

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>InMemoryPreferences</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param parent \u7528\u4e8e\u521d\u59cb\u5316 <code>InMemoryPreferences</code> \u7684 <code>AbstractPreferences</code> \u503c
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         */
        private InMemoryPreferences(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        /**
         * \u8c03\u7528 <code>values.put()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param key \u7528\u4e8e\u5728\u6d4b\u8bd5\u4e0a\u4e0b\u6587\u6216\u7f13\u5b58\u4e2d\u7d22\u5f15\u6570\u636e\u7684\u952e
         * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
         */
        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        /**
         * \u901a\u8fc7 <code>values.get()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param key \u7528\u4e8e\u5728\u6d4b\u8bd5\u4e0a\u4e0b\u6587\u6216\u7f13\u5b58\u4e2d\u7d22\u5f15\u6570\u636e\u7684\u952e
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>values.get(key)</code>
         */
        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        /**
         * \u8c03\u7528 <code>values.remove()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param key \u7528\u4e8e\u5728\u6d4b\u8bd5\u4e0a\u4e0b\u6587\u6216\u7f13\u5b58\u4e2d\u7d22\u5f15\u6570\u636e\u7684\u952e
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
         * \u8c03\u7528 <code>values.clear()</code>\u3001<code>children.clear()</code>\u3001<code>parent()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @throws BackingStoreException \u5c1d\u8bd5\u6267\u884c\u5f53\u524d\u6d4b\u8bd5\u64cd\u4f5c\u5931\u8d25
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
         * \u901a\u8fc7 <code>values.keySet()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>values.keySet().toArray(new String[0])</code>
         * @throws BackingStoreException \u5c1d\u8bd5\u6267\u884c\u5f53\u524d\u6d4b\u8bd5\u64cd\u4f5c\u5931\u8d25
         */
        @Override
        protected String[] keysSpi() throws BackingStoreException {
            return values.keySet().toArray(new String[0]);
        }

        /**
         * \u901a\u8fc7 <code>children.keySet()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>children.keySet().toArray(new String[0])</code>
         * @throws BackingStoreException \u5c1d\u8bd5\u6267\u884c\u5f53\u524d\u6d4b\u8bd5\u64cd\u4f5c\u5931\u8d25
         */
        @Override
        protected String[] childrenNamesSpi() throws BackingStoreException {
            return children.keySet().toArray(new String[0]);
        }

        /**
         * \u6309 <code>(Objects.nonNull(child))</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>child</code>\u3001<code>Objects.nonNull(existing) ? existing : candidate</code>
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
         * \u5f53\u524d\u5b9e\u73b0\u53ea\u4fdd\u5b58\u5185\u5b58\u72b6\u6001\uff0c\u5185\u5b58\u504f\u597d\u8282\u70b9\u65e0\u9700\u540c\u6b65\u5230\u5916\u90e8\u5b58\u50a8\u3002
         * @throws BackingStoreException Preferences SPI \u58f0\u660e\u7684\u5916\u90e8\u5b58\u50a8\u5f02\u5e38
         */
        @Override
        protected void syncSpi() throws BackingStoreException {
            // 内存节点无需与外部存储同步。
        }

        /**
         * \u5f53\u524d\u5b9e\u73b0\u53ea\u4fdd\u5b58\u5185\u5b58\u72b6\u6001\uff0c\u5185\u5b58\u504f\u597d\u8282\u70b9\u65e0\u9700\u5199\u5165\u5916\u90e8\u5b58\u50a8\u3002
         * @throws BackingStoreException Preferences SPI \u58f0\u660e\u7684\u5916\u90e8\u5b58\u50a8\u5f02\u5e38
         */
        @Override
        protected void flushSpi() throws BackingStoreException {
            // 内存节点无需刷新到外部存储。
        }

        /** \u8c03\u7528 <code>children.values()</code>\u3001<code>child.clearAll()</code>\u3001<code>children.clear()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        private void clearAll() {
            for (InMemoryPreferences child : children.values()) {
                child.clearAll();
            }
            children.clear();
            values.clear();
        }
    }
}
