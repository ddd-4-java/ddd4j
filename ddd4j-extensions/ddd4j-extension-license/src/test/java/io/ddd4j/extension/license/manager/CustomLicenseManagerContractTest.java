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
package io.ddd4j.extension.license.manager;

import io.ddd4j.extension.license.LicenseExtraModel;
import io.ddd4j.extension.license.machine.LicenseMachineInfoProvider;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 许可证机器绑定在三条 JDK 线上的共同错误契约。 */
class CustomLicenseManagerContractTest {

    @Test
    void missingMachineSerialNumberReportsLicenseMismatch() {
        LicenseMachineInfoProvider provider = new LicenseMachineInfoProvider() {
            @Override
            public Set<String> ipAddresses() {
                return Collections.emptySet();
            }

            @Override
            public Set<String> macAddresses() {
                return Collections.emptySet();
            }

            @Override
            public String serialNumber() {
                return null;
            }
        };
        LicenseExtraModel expected = new LicenseExtraModel();
        expected.setSn("licensed-serial");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new CustomLicenseManager(provider).validateExtra(expected));

        assertEquals("SN 校验失败: 当前机器序列号不在授权范围内", error.getMessage());
    }
}
