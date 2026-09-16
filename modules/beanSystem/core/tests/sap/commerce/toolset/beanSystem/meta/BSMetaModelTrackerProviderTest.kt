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

package sap.commerce.toolset.beanSystem.meta

import com.intellij.openapi.project.Project
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BSMetaModelTrackerProviderTest {

    private val project = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Project::class.java)) { _, _, _ -> null } as Project
    private val keyResolver = BSMetaModelTrackerProvider().createKeyResolver(project)

    @Test
    fun `key is the file name of a beans definition file`() {
        assertEquals("core-beans.xml", keyResolver("core-beans.xml", "/hybris/bin/platform/core/resources/core-beans.xml"))
    }

    @Test
    fun `key is null for a items definition file`() {
        assertNull(keyResolver("core-items.xml", "/hybris/bin/platform/core/resources/core-items.xml"))
    }

    @Test
    fun `key is null for a file without the extension name prefix`() {
        assertNull(keyResolver("beans.xml", "/hybris/bin/custom/beans.xml"))
    }
}
