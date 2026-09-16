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

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentModulesVerifierTest {

    private val root = createTempDirectory("content-modules").toFile()
    private val platform = File(root, "ide")
    private val plugin = File(root, "sandbox/our")

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `class of a content module of a plugin dependency is not reachable`() {
        val result = verify(moduleDependencies = """<plugin id="other"/>""")

        assertEquals(1, result.problems.size)
        assertEquals("our.feature", result.problems.first().classLoader)
        assertEquals("module:other.api (visibility=public)", result.problems.first().provider)
    }

    @Test
    fun `class of a content module is reachable through the module dependency`() {
        val result = verify(moduleDependencies = """<module name="other.api"/>""")

        assertTrue(result.problems.isEmpty())
    }

    @Test
    fun `optional content module reaches depends of the plugin`() {
        val result = verify(pluginDepends = "<depends>other</depends>")

        assertTrue(result.problems.isEmpty())
    }

    @Test
    fun `required content module does not reach depends of the plugin`() {
        val result = verify(pluginDepends = "<depends>other</depends>", loading = "required")

        assertEquals(1, result.problems.size)
    }

    @Test
    fun `dependency without class references is reported as unused`() {
        val result = verify(moduleDependencies = """<module name="other.api"/><plugin id="unused"/>""")

        assertEquals(listOf("plugin unused"), result.unusedDependencies.map { it.dependency })
    }

    private fun verify(
        moduleDependencies: String = "",
        pluginDepends: String = "",
        loading: String? = null,
    ): ContentModulesVerifier.Result {
        jar(File(platform, "lib/core.jar"), mapOf("core/Core.class" to ByteArray(0)))
        jar(
            File(platform, "plugins/other/lib/other.jar"), mapOf(
                "META-INF/plugin.xml" to """
                    <idea-plugin>
                        <id>other</id>
                        <content>
                            <module name="other.api"><![CDATA[<idea-plugin visibility="public"/>]]></module>
                        </content>
                    </idea-plugin>
                """.trimIndent().toByteArray(),
                "other/Main.class" to ByteArray(0),
            )
        )
        jar(File(platform, "plugins/other/lib/modules/other.api.jar"), mapOf("${FIXTURES}ApiFixture.class" to ByteArray(0)))

        val loadingAttribute = loading?.let { """ loading="$it"""" }.orEmpty()
        jar(
            File(plugin, "lib/our.jar"), mapOf(
                "META-INF/plugin.xml" to """
                    <idea-plugin>
                        <id>our</id>
                        $pluginDepends
                        <content>
                            <module name="our.feature"$loadingAttribute/>
                        </content>
                    </idea-plugin>
                """.trimIndent().toByteArray(),
            )
        )
        jar(
            File(plugin, "lib/modules/our.feature.jar"), mapOf(
                "our.feature.xml" to "<idea-plugin><dependencies>$moduleDependencies</dependencies></idea-plugin>".toByteArray(),
                "${FIXTURES}ConsumerFixture.class" to classBytes("ConsumerFixture"),
            )
        )

        return ContentModulesVerifier(platform, plugin).verify()
    }

    private fun classBytes(name: String) = javaClass.classLoader.getResourceAsStream("$FIXTURES$name.class")!!.use { it.readBytes() }

    private fun jar(file: File, entries: Map<String, ByteArray>) {
        file.parentFile.mkdirs()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    companion object {
        private const val FIXTURES = "sap/commerce/toolset/gradle/contentModules/"
    }
}
