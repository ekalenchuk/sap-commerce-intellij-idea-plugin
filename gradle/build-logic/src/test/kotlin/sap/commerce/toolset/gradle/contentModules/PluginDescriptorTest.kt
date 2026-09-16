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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginDescriptorTest {

    private val descriptor = PluginDescriptor.parse(
        """
        <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
            <id>com.example</id>
            <module value="com.example.alias"/>
            <depends>com.intellij.java</depends>
            <depends optional="true" config-file="optional.xml">com.intellij.spring</depends>
            <dependencies>
                <plugin id="com.intellij.mcpServer"/>
                <module name="intellij.grid.impl"/>
            </dependencies>
            <extensions defaultExtensionNs="org.jetbrains.plugins.terminal"/>
            <content>
                <module name="example.optional"/>
                <module name="example.required" loading="required"/>
                <module name="example.embedded"><![CDATA[<idea-plugin visibility="public"><dependencies><module name="example.optional"/></dependencies></idea-plugin>]]></module>
            </content>
        </idea-plugin>
        """.trimIndent()
    )

    @Test
    fun `plugin id and aliases are parsed`() {
        assertEquals("com.example", descriptor.id)
        assertEquals(listOf("com.example.alias"), descriptor.aliases)
    }

    @Test
    fun `depends include optional ones`() {
        assertEquals(listOf("com.intellij.java", "com.intellij.spring"), descriptor.depends)
    }

    @Test
    fun `dependencies are split into plugins and modules`() {
        assertEquals(listOf("com.intellij.mcpServer"), descriptor.pluginDependencies)
        assertEquals(listOf("intellij.grid.impl"), descriptor.moduleDependencies)
    }

    @Test
    fun `extension namespaces are parsed`() {
        assertEquals(setOf("org.jetbrains.plugins.terminal"), descriptor.extensionNamespaces)
    }

    @Test
    fun `optional content module has the plugin class loader as an implicit parent`() {
        assertTrue(descriptor.contentModules.first { it.name == "example.optional" }.hasImplicitPluginParent)
    }

    @Test
    fun `required content module has no implicit parent`() {
        assertFalse(descriptor.contentModules.first { it.name == "example.required" }.hasImplicitPluginParent)
    }

    @Test
    fun `embedded content module descriptor is parsed`() {
        val embedded = PluginDescriptor.parse(descriptor.contentModules.first { it.name == "example.embedded" }.embeddedDescriptor!!)

        assertEquals("public", embedded.visibility)
        assertEquals(listOf("example.optional"), embedded.moduleDependencies)
    }
}
