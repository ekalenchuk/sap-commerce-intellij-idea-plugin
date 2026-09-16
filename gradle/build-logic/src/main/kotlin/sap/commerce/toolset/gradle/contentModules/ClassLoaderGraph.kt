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

/**
 * Class loaders of the IntelliJ Platform plugin set: a node is either a plugin main class loader or a content module class loader,
 * an edge is a parent class loader. Class lookup delegates to all parents transitively; core classes are visible everywhere.
 */
class ClassLoaderGraph(private val coreClasses: Set<String>) {

    private val classProviders = HashMap<String, MutableList<String>>()
    private val parents = HashMap<String, MutableSet<String>>()
    private val pluginModules = HashMap<String, MutableSet<String>>()
    private val pluginAliases = HashMap<String, String>()

    fun addPlugin(id: String, classes: Set<String>, aliases: Collection<String> = emptyList()) {
        addNode(pluginNode(id), classes)
        aliases.forEach { pluginAliases[it] = id }
    }

    fun addContentModule(pluginId: String, name: String, classes: Set<String>) {
        addNode(moduleNode(name), classes)
        pluginModules.getOrPut(pluginId) { mutableSetOf() }.add(name)
    }

    fun addParent(node: String, parent: String) {
        if (node != parent) parents.getOrPut(node) { mutableSetOf() }.add(parent)
    }

    private fun contentModules(pluginId: String): Set<String> = pluginModules[resolvePluginId(pluginId)].orEmpty()

    fun resolvePluginId(id: String) = pluginAliases[id] ?: id

    /**
     * `<depends>` in a plugin descriptor: the plugin main class loader and all its content modules.
     */
    fun dependsParents(pluginId: String) = contentModules(pluginId).map { moduleNode(it) } + pluginNode(resolvePluginId(pluginId))

    /**
     * `<plugin id>` in `<dependencies>`: only the plugin main class loader.
     */
    fun pluginParent(pluginId: String) = pluginNode(resolvePluginId(pluginId))

    fun reachable(node: String): Set<String> {
        val visited = LinkedHashSet<String>()
        val queue = ArrayDeque(listOf(node))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (visited.add(current)) queue.addAll(parents[current].orEmpty())
        }
        return visited
    }

    fun isVisible(className: String, reachable: Set<String>) = className in coreClasses
        || classProviders[className].orEmpty().any { it in reachable }

    /**
     * Node providing the class or `null` for classes unknown to the plugin set, e.g. JDK classes.
     */
    fun provider(className: String): String? = classProviders[className]?.firstOrNull()

    fun providers(className: String): List<String> = classProviders[className].orEmpty()

    private fun addNode(node: String, classes: Set<String>) {
        classes.forEach { classProviders.getOrPut(it) { mutableListOf() }.add(node) }
    }

    companion object {
        fun pluginNode(id: String) = "plugin:$id"
        fun moduleNode(name: String) = "module:$name"
    }
}
