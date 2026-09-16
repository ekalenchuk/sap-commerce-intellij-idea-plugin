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
 * Reads internal names of the classes referenced by a class file:
 * `CONSTANT_Class` entries and types used in member and method type descriptors.
 */
object ClassReferences {

    private const val MAGIC = 0xCAFEBABE.toInt()
    private val DESCRIPTOR_TYPE = Regex("L([^;<]+);")

    fun read(bytes: ByteArray): Set<String> {
        if (bytes.size < 10 || u4(bytes, 0) != MAGIC) return emptySet()

        val count = u2(bytes, 8)
        val utf8 = HashMap<Int, String>()
        val classIndexes = mutableListOf<Int>()
        val descriptorIndexes = mutableListOf<Int>()

        var offset = 10
        var index = 1
        while (index < count && offset < bytes.size) {
            when (bytes[offset].toInt()) {
                1 -> {
                    val length = u2(bytes, offset + 1)
                    utf8[index] = String(bytes, offset + 3, length, Charsets.UTF_8)
                    offset += 3 + length
                }

                7 -> {
                    classIndexes.add(u2(bytes, offset + 1))
                    offset += 3
                }

                16 -> {
                    descriptorIndexes.add(u2(bytes, offset + 1))
                    offset += 3
                }

                8, 19, 20 -> offset += 3
                12 -> {
                    descriptorIndexes.add(u2(bytes, offset + 3))
                    offset += 5
                }

                3, 4, 9, 10, 11, 17, 18 -> offset += 5
                5, 6 -> {
                    offset += 9
                    index++
                }

                15 -> offset += 4
                else -> break
            }
            index++
        }

        val classes = classIndexes
            .mapNotNull { utf8[it] }
            .map { it.trimStart('[') }
            .map { if (it.startsWith('L') && it.endsWith(';')) it.substring(1, it.length - 1) else it }
            .filter { it.length > 1 }
        val descriptorTypes = descriptorIndexes
            .mapNotNull { utf8[it] }
            .flatMap { descriptor -> DESCRIPTOR_TYPE.findAll(descriptor).map { it.groupValues[1] } }

        return (classes + descriptorTypes).toSet()
    }

    private fun u2(bytes: ByteArray, offset: Int) = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun u4(bytes: ByteArray, offset: Int) = (u2(bytes, offset) shl 16) or u2(bytes, offset + 2)
}
