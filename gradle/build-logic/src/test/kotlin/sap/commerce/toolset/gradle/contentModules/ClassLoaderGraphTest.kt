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

import sap.commerce.toolset.gradle.contentModules.ClassLoaderGraph.Companion.moduleNode
import sap.commerce.toolset.gradle.contentModules.ClassLoaderGraph.Companion.pluginNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassLoaderGraphTest {

    private val graph = ClassLoaderGraph(setOf("core/Core")).apply {
        addPlugin("other", setOf("other/Main"), aliases = listOf("com.other.alias"))
        addContentModule("other", "other.api", setOf("other/Api"))
        addPlugin("our", setOf("our/Main"))
        addContentModule("our", "our.feature", setOf("our/Feature"))
    }
    private val feature = moduleNode("our.feature")

    @Test
    fun `plugin dependency exposes only the plugin main class loader`() {
        graph.addParent(feature, graph.pluginParent("other"))
        val reachable = graph.reachable(feature)

        assertTrue(graph.isVisible("other/Main", reachable))
        assertFalse(graph.isVisible("other/Api", reachable))
    }

    @Test
    fun `depends exposes the plugin content modules`() {
        graph.dependsParents("other").forEach { graph.addParent(feature, it) }

        assertTrue(graph.isVisible("other/Api", graph.reachable(feature)))
    }

    @Test
    fun `parent class loaders are reachable transitively`() {
        graph.addParent(feature, pluginNode("our"))
        graph.dependsParents("other").forEach { graph.addParent(pluginNode("our"), it) }

        assertTrue(graph.isVisible("other/Api", graph.reachable(feature)))
    }

    @Test
    fun `plugin alias resolves to the plugin main class loader`() {
        assertEquals(pluginNode("other"), graph.pluginParent("com.other.alias"))
    }

    @Test
    fun `core classes are visible without dependencies`() {
        assertTrue(graph.isVisible("core/Core", graph.reachable(feature)))
    }

    @Test
    fun `own classes are visible`() {
        assertTrue(graph.isVisible("our/Feature", graph.reachable(feature)))
    }

    @Test
    fun `provider of a class unknown to the plugin set is null`() {
        assertNull(graph.provider("java/lang/String"))
    }
}
