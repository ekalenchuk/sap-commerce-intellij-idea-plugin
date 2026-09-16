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
import java.io.File
import java.util.zip.ZipFile

/**
 * Verifies that every class referenced from the plugin and its content modules is reachable through the class loaders
 * configured by the IntelliJ Platform:
 *
 * - plugin main class loader: plugin jars, `<depends>` plugins with all their content modules, `<dependencies>`
 * - content module: own jar, `<dependencies>` (`<plugin>` exposes only the plugin main jar), plugin main class loader for optional modules
 * - class lookup delegates to parents transitively, platform core classes are visible everywhere
 *
 * Plugin Verifier does not model class loaders of content modules, so a missing dependency is reported only at runtime.
 */
class ContentModulesVerifier(
    private val platformDirectory: File,
    private val pluginDirectory: File,
    private val otherPluginDirectories: Collection<File> = emptyList(),
    private val scannedPackagePrefixes: Collection<String> = emptyList(),
) {

    data class Problem(val classLoader: String, val provider: String, val referencedBy: Set<String>)
    data class UnusedDependency(val module: String, val dependency: String)
    data class Result(
        val pluginId: String,
        val contentModules: Int,
        val problems: List<Problem>,
        val unusedDependencies: List<UnusedDependency>,
    )

    private class IndexedModule(
        val module: PluginDescriptor.ContentModule,
        val descriptor: PluginDescriptor,
        val classes: Set<String>,
        val references: Map<String, Set<String>>,
    )

    private class IndexedPlugin(
        val id: String,
        val descriptor: PluginDescriptor,
        val classes: Set<String>,
        val references: Map<String, Set<String>>,
        val modules: List<IndexedModule>,
    )

    fun verify(): Result {
        val graph = ClassLoaderGraph(coreClasses())
        val platformPlugins = (File(platformDirectory, "plugins").listFiles().orEmpty().toList() + otherPluginDirectories)
            .filter { it.isDirectory && it.canonicalFile != pluginDirectory.canonicalFile }
            .mapNotNull { readPlugin(it, collectReferences = false) }
        val plugin = readPlugin(pluginDirectory, collectReferences = true)
            ?: error("Plugin descriptor not found in $pluginDirectory")
        val visibility = (platformPlugins + plugin)
            .flatMap { it.modules }
            .associate { it.module.name to it.descriptor.visibility }

        (platformPlugins + plugin).forEach { indexed ->
            graph.addPlugin(indexed.id, indexed.classes, indexed.descriptor.aliases)
            indexed.modules.forEach { graph.addContentModule(indexed.id, it.module.name, it.classes) }
        }

        platformPlugins.forEach { indexed ->
            graph.addParents(pluginNode(indexed.id), indexed.descriptor)
            indexed.modules.forEach {
                graph.addParents(moduleNode(it.module.name), it.descriptor)
                // lenient for the platform plugins: the implicit parent can only hide our problems, never report false ones
                graph.addParent(moduleNode(it.module.name), pluginNode(indexed.id))
            }
        }

        val mainNode = pluginNode(plugin.id)
        graph.addParents(mainNode, plugin.descriptor)
        plugin.modules.forEach {
            val node = if (it.module.isEmbedded) mainNode else moduleNode(it.module.name)
            graph.addParents(node, it.descriptor)
            if (it.module.hasImplicitPluginParent) graph.addParent(node, mainNode)
        }

        fun describe(node: String) = node.removePrefix("module:")
            .takeIf { node.startsWith("module:") }
            ?.let { name -> visibility[name]?.let { "$node (visibility=$it)" } }
            ?: node

        val problems = buildList {
            addAll(check(graph, "plugin class loader", plugin.references, graph.reachable(mainNode), ::describe))
            plugin.modules
                .filterNot { it.module.isEmbedded }
                .forEach { addAll(check(graph, it.module.name, it.references, graph.reachable(moduleNode(it.module.name)), ::describe)) }
        }

        val unusedDependencies = plugin.modules.flatMap { unusedDependencies(graph, it) }

        return Result(plugin.id, plugin.modules.size, problems, unusedDependencies)
    }

    private fun check(
        graph: ClassLoaderGraph,
        classLoader: String,
        references: Map<String, Set<String>>,
        reachable: Set<String>,
        describe: (String) -> String,
    ): List<Problem> = references
        .filterNot { (reference, _) -> graph.isVisible(reference, reachable) }
        .mapNotNull { (reference, referencedBy) -> graph.provider(reference)?.let { it to referencedBy } }
        .groupBy({ it.first }, { it.second })
        .map { (provider, referencedBy) -> Problem(classLoader, describe(provider), referencedBy.flatten().toSortedSet()) }
        .sortedBy { it.provider }

    private fun unusedDependencies(graph: ClassLoaderGraph, indexed: IndexedModule): List<UnusedDependency> {
        val usedNodes = indexed.references.keys.flatMap { graph.providers(it) }.toSet()
        val plugins = indexed.descriptor.pluginDependencies
            .filterNot { pluginId -> graph.dependsParents(pluginId).any { it in usedNodes } }
            .filterNot { pluginId -> indexed.descriptor.extensionNamespaces.any { it.startsWith(pluginId) } }
            .map { "plugin $it" }
        val modules = indexed.descriptor.moduleDependencies
            .filterNot { moduleNode(it) in usedNodes }
            .map { "module $it" }

        return (plugins + modules).map { UnusedDependency(indexed.module.name, it) }
    }

    private fun ClassLoaderGraph.addParents(node: String, descriptor: PluginDescriptor) {
        descriptor.depends.flatMap { dependsParents(it) }.forEach { addParent(node, it) }
        descriptor.pluginDependencies.forEach { addParent(node, pluginParent(it)) }
        descriptor.moduleDependencies.forEach { addParent(node, moduleNode(it)) }
    }

    private fun coreClasses(): Set<String> = File(platformDirectory, "lib").walkTopDown()
        .filter { it.isFile && it.name.endsWith(".jar") }
        .flatMap { readJar(it, collectReferences = false).first }
        .toSet()

    private fun readPlugin(directory: File, collectReferences: Boolean): IndexedPlugin? {
        val lib = File(directory, "lib").takeIf { it.isDirectory } ?: return null
        val jars = lib.listFiles { file -> file.isFile && file.name.endsWith(".jar") }.orEmpty().sortedBy { it.name }
        val descriptor = jars.firstNotNullOfOrNull { readEntry(it, "META-INF/plugin.xml") }
            ?.let { runCatching { PluginDescriptor.parse(it) }.getOrNull() }
            ?: return null
        val id = descriptor.id ?: return null

        val classes = HashSet<String>()
        val references = HashMap<String, MutableSet<String>>()
        jars.forEach { jar ->
            val (jarClasses, jarReferences) = readJar(jar, collectReferences)
            classes.addAll(jarClasses)
            jarReferences.forEach { (reference, referencedBy) -> references.getOrPut(reference) { HashSet() }.addAll(referencedBy) }
        }

        val modules = descriptor.contentModules.map { module ->
            val jar = File(lib, "modules/${module.name.replace('/', '.')}.jar").takeIf { it.isFile }
            val moduleDescriptor = (module.embeddedDescriptor ?: jar?.let { readEntry(it, "${module.name}.xml") })
                ?.let { runCatching { PluginDescriptor.parse(it) }.getOrNull() }
                ?: PluginDescriptor()
            val (moduleClasses, moduleReferences) = jar
                ?.let { readJar(it, collectReferences) }
                ?: (emptySet<String>() to emptyMap())

            IndexedModule(module, moduleDescriptor, moduleClasses, moduleReferences)
        }

        return IndexedPlugin(id, descriptor, classes, references, modules)
    }

    private fun readJar(jar: File, collectReferences: Boolean): Pair<Set<String>, Map<String, Set<String>>> = runCatching {
        ZipFile(jar).use { zip ->
            val classes = HashSet<String>()
            val references = HashMap<String, MutableSet<String>>()
            zip.entries().asSequence()
                .filter { it.name.endsWith(".class") && !it.name.startsWith("META-INF/") }
                .forEach { entry ->
                    val className = entry.name.removeSuffix(".class")
                    classes.add(className)

                    if (collectReferences && (scannedPackagePrefixes.isEmpty() || scannedPackagePrefixes.any { className.startsWith(it) })) {
                        ClassReferences.read(zip.getInputStream(entry).use { it.readBytes() })
                            .forEach { references.getOrPut(it) { HashSet() }.add(className) }
                    }
                }
            classes to references
        }
    }.getOrDefault(emptySet<String>() to emptyMap())

    private fun readEntry(jar: File, name: String): String? = runCatching {
        ZipFile(jar).use { zip -> zip.getEntry(name)?.let { entry -> zip.getInputStream(entry).use { it.readBytes().decodeToString() } } }
    }.getOrNull()
}
