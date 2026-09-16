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

package sap.commerce.toolset.gradle.api

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*
import sap.commerce.toolset.gradle.contentModules.ContentModulesVerifier

/**
 * Verifies class loader visibility of the Plugin Model v2 content modules in the prepared sandbox:
 * every class referenced by the plugin must be reachable through the declared `<dependencies>`.
 * See [ContentModulesVerifier] for the class loader rules.
 *
 * Declared dependencies without any class reference are listed in the report only, as they may be intended to gate module loading.
 */
abstract class CxVerifyContentModulesGradleTask : DefaultTask() {

    /**
     * Plugin directory in the sandbox: jars in `lib` and content module jars in `lib/modules`.
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pluginDirectory: DirectoryProperty

    /**
     * IntelliJ Platform installation, tracked by its path only.
     */
    @get:Internal
    abstract val platformDirectory: DirectoryProperty

    /**
     * Sandbox plugins directory with other installed plugins, e.g. compatible plugins from the JetBrains Marketplace.
     */
    @get:Internal
    abstract val sandboxPluginsDirectory: DirectoryProperty

    /**
     * Package prefixes (`sap/commerce/`) of the classes to verify, empty to verify all classes including bundled libraries.
     */
    @get:Input
    abstract val scannedPackagePrefixes: ListProperty<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Input
    val platformPath: String
        get() = platformDirectory.get().asFile.absolutePath

    init {
        group = "verification"
        description = "Verifies class loader visibility of the plugin content modules."
        scannedPackagePrefixes.convention(emptyList())
    }

    @TaskAction
    fun verify() {
        val plugin = pluginDirectory.get().asFile
        val result = ContentModulesVerifier(
            platformDirectory = platformDirectory.get().asFile,
            pluginDirectory = plugin,
            otherPluginDirectories = sandboxPluginsDirectory.orNull?.asFile?.listFiles()
                ?.filter { it.isDirectory && it.canonicalFile != plugin.canonicalFile }
                .orEmpty(),
            scannedPackagePrefixes = scannedPackagePrefixes.get(),
        ).verify()

        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        report.writeText(render(result))

        if (result.unusedDependencies.isNotEmpty()) {
            logger.lifecycle("${result.unusedDependencies.size} declared dependencies of the content modules have no class references, see $report")
        }

        if (result.problems.isNotEmpty()) {
            throw GradleException(
                "Referenced classes are not reachable through the declared dependencies, " +
                    "declare the missing <module>/<plugin> dependencies:\n" +
                    renderProblems(result) +
                    "\nSee $report"
            )
        }

        logger.lifecycle("Verified class loaders of ${result.contentModules} content modules of ${result.pluginId}")
    }

    private fun render(result: ContentModulesVerifier.Result) = buildString {
        appendLine("Class loader verification of ${result.pluginId} (${result.contentModules} content modules)")
        appendLine()
        appendLine("Unreachable classes:")
        append(renderProblems(result).ifEmpty { "  none\n" })
        appendLine()
        appendLine("Declared dependencies without class references (they still gate module loading):")
        if (result.unusedDependencies.isEmpty()) appendLine("  none")
        result.unusedDependencies
            .groupBy({ it.module }, { it.dependency })
            .forEach { (module, dependencies) -> appendLine("  $module: ${dependencies.joinToString()}") }
    }

    private fun renderProblems(result: ContentModulesVerifier.Result) = buildString {
        result.problems
            .groupBy { it.classLoader }
            .forEach { (classLoader, problems) ->
                appendLine("  $classLoader")
                problems.forEach {
                    val others = it.referencedBy.size - 1
                    val suffix = if (others > 0) " and $others more" else ""
                    appendLine("    ${it.provider}: referenced from ${it.referencedBy.first().replace('/', '.')}$suffix")
                }
            }
    }
}
