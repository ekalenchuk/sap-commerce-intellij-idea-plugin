/*
 * This file is part of "SAP Commerce Developers Toolset" plugin for IntelliJ IDEA.
 * Copyright (C) 2019-2025 EPAM Systems <hybrisideaplugin@epam.com> and contributors
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

package com.intellij.idea.plugin.hybris.settings

import com.intellij.openapi.components.BaseState

data class ImpexSettings(
    var groupLocalizedFiles: Boolean = true,
    var editMode: ImpExEditModeSettings = ImpExEditModeSettings(),
    var folding: ImpexFoldingSettings = ImpexFoldingSettings(),
    var completion: ImpexCompletionSettings = ImpexCompletionSettings(),
    var documentation: ImpexDocumentationSettings = ImpexDocumentationSettings(),
)

data class ImpExEditModeSettings(
    var firstRowIsHeader: Boolean = true,
    var trimWhitespace: Boolean = false,
)

data class ImpexFoldingSettings(
    var enabled: Boolean = true,
    var useSmartFolding: Boolean = true,
    var foldMacroInParameters: Boolean = true,
)

data class ImpexDocumentationSettings(
    var enabled: Boolean = true,
    var showTypeDocumentation: Boolean = true,
    var showModifierDocumentation: Boolean = true,
)

class ImpexCompletionSettings : BaseState() {
    var showInlineTypes by property(true)
    var addCommaAfterInlineType by property(true)
    var addEqualsAfterModifier by property(true)
}
