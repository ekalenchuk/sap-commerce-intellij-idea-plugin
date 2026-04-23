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

package sap.commerce.toolset.localextensions

import java.nio.file.Path

/**
 * Represents the path tag stored in localextension.xml stored as is except normalized type. The path tag is responsible for loading order of extensions.
 * The dir represents the folder from which extensions will be loaded. Value examples: ${property}, /path/to/folder.
 * The normalizedPath represents the resolved dir path, that verifies that the dir actually exists. The property is resolved based on the expanded properties,
 * that's why it's important to build the platform before the project import.
 *
 * Official documentation available here https://help.sap.com/docs/SAP_COMMERCE/b490bb4e85bc42a7aa09d513d0bcb18e/cce26d8ef435425fb9e054d91794148c.html.
 **/
data class ScanType(
    val dir: String,
    val autoload: Boolean,
    var depth: Int,
    val normalizedPath: Path,
)