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

package sap.commerce.toolset.gradle.contentModules

import kotlin.test.Test
import kotlin.test.assertTrue

class ClassReferencesTest {

    private val references = ClassReferences.read(
        javaClass.classLoader.getResourceAsStream("sap/commerce/toolset/gradle/contentModules/ConsumerFixture.class")!!.use { it.readBytes() }
    )

    @Test
    fun `field and constructor call types are referenced`() {
        assertTrue("sap/commerce/toolset/gradle/contentModules/ApiFixture" in references)
    }

    @Test
    fun `method return type is referenced`() {
        assertTrue("java/util/regex/Pattern" in references)
    }

    @Test
    fun `primitive array descriptors are not referenced as classes`() {
        assertTrue(references.none { it.length == 1 })
    }

    @Test
    fun `non class file content has no references`() {
        assertTrue(ClassReferences.read("not a class file".toByteArray()).isEmpty())
    }
}
