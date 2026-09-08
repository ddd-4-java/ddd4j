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

    private static final InMemoryPreferences SYSTEM_ROOT = new InMemoryPreferences(null, "");
    private static final InMemoryPreferences USER_ROOT = new InMemoryPreferences(null, "");
    private static volatile RemoveFailureMode removeFailureMode = RemoveFailureMode.NONE;

    @Override
    public Preferences systemRoot() {
        return SYSTEM_ROOT;
    }

    @Override
    public Preferences userRoot() {
        return USER_ROOT;
    }

    static boolean isUserRoot(Preferences preferences) {
        return Objects.equals(USER_ROOT, preferences);
    }

    static void setRemoveFailure(boolean enabled) {
        removeFailureMode = enabled ? RemoveFailureMode.RUNTIME : RemoveFailureMode.NONE;
    }

    static void setRemoveFailureMode(RemoveFailureMode mode) {
        removeFailureMode = Objects.requireNonNull(mode, "mode");
    }

    static void reset() {
        removeFailureMode = RemoveFailureMode.NONE;
        SYSTEM_ROOT.clearAll();
        USER_ROOT.clearAll();
    }

    enum RemoveFailureMode {
        NONE,
        RUNTIME,
        ERROR
    }

    private static final class InMemoryPreferences extends AbstractPreferences {

        private final Map<String, InMemoryPreferences> children = new ConcurrentHashMap<>();
        private final Map<String, String> values = new ConcurrentHashMap<>();

        private InMemoryPreferences(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

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

        @Override
        protected void removeNodeSpi() throws BackingStoreException {
            values.clear();
            children.clear();
            Preferences parent = parent();
            if (parent instanceof InMemoryPreferences) {
                ((InMemoryPreferences) parent).children.remove(name());
            }
        }

        @Override
        protected String[] keysSpi() throws BackingStoreException {
            return values.keySet().toArray(new String[0]);
        }

        @Override
        protected String[] childrenNamesSpi() throws BackingStoreException {
            return children.keySet().toArray(new String[0]);
        }

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

        @Override
        protected void syncSpi() throws BackingStoreException {
            // 内存节点无需与外部存储同步。
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
            // 内存节点无需刷新到外部存储。
        }

        private void clearAll() {
            for (InMemoryPreferences child : children.values()) {
                child.clearAll();
            }
            children.clear();
            values.clear();
        }
    }
}
