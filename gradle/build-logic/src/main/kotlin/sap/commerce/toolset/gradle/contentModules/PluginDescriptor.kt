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

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Class loading related part of a plugin (`plugin.xml`) or content module descriptor.
 */
data class PluginDescriptor(
    val id: String? = null,
    val visibility: String? = null,
    val aliases: List<String> = emptyList(),
    val depends: List<String> = emptyList(),
    val pluginDependencies: List<String> = emptyList(),
    val moduleDependencies: List<String> = emptyList(),
    val contentModules: List<ContentModule> = emptyList(),
    val extensionNamespaces: Set<String> = emptySet(),
) {

    data class ContentModule(
        val name: String,
        val loading: String?,
        val embeddedDescriptor: String?,
    ) {
        /**
         * Optional modules get the plugin main class loader as an implicit parent, required ones do not.
         */
        val hasImplicitPluginParent: Boolean
            get() = loading == null || loading == "optional" || loading == "on-demand"
        val isEmbedded: Boolean
            get() = loading == "embedded"
    }

    companion object {
        private val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }

        fun parse(xml: String): PluginDescriptor {
            val root = synchronized(factory) { factory.newDocumentBuilder() }
                .parse(xml.byteInputStream())
                .documentElement

            return PluginDescriptor(
                id = root.children("id").firstOrNull()?.textContent?.trim(),
                visibility = root.getAttribute("visibility").ifBlank { null },
                aliases = root.children("module").mapNotNull { it.getAttribute("value").ifBlank { null } },
                depends = root.children("depends").map { it.textContent.trim() },
                pluginDependencies = root.children("dependencies").flatMap { it.children("plugin") }.map { it.getAttribute("id") },
                moduleDependencies = root.children("dependencies").flatMap { it.children("module") }.map { it.getAttribute("name") },
                contentModules = root.children("content").flatMap { it.children("module") }.map {
                    ContentModule(
                        name = it.getAttribute("name"),
                        loading = it.getAttribute("loading").ifBlank { null },
                        embeddedDescriptor = it.textContent.trim().takeIf { text -> text.startsWith("<") },
                    )
                },
                extensionNamespaces = root.children("extensions").map { it.getAttribute("defaultExtensionNs") }.toSet(),
            )
        }

        private fun Element.children(tagName: String): List<Element> = (0 until childNodes.length)
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName == tagName }
    }
}
