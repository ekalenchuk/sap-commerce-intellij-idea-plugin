/*
 * This file is part of "SAP Commerce Developers Toolset" plugin for IntelliJ IDEA.
 * Copyright (C) 2019-2026 EPAM Systems <hybrisideaplugin@epam.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package sap.commerce.toolset.credentials

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class CxCredentialStoreTest {

    @Test
    fun `service name starts with the prefix served by the JetBrains Client`() {
        val serviceName = CxCredentialStore.serviceName("11111111-2222-3333-4444-555555555555")

        assertTrue(
            serviceName.startsWith("IntelliJ Platform"),
            "Remote Development denies credentials of any other service name, was: $serviceName"
        )
    }

    @Test
    fun `service name keeps the subsystem and the key`() {
        val serviceName = CxCredentialStore.serviceName("proxy - 42")

        assertContains(serviceName, "SAP CX")
        assertContains(serviceName, "proxy - 42")
    }
}
