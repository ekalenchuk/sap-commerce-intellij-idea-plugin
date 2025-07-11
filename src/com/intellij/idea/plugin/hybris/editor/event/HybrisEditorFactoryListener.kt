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

package com.intellij.idea.plugin.hybris.editor.event

import com.intellij.idea.plugin.hybris.acl.file.AclFileToolbarInstaller
import com.intellij.idea.plugin.hybris.acl.file.AclFileType
import com.intellij.idea.plugin.hybris.flexibleSearch.file.FlexibleSearchFileToolbarInstaller
import com.intellij.idea.plugin.hybris.flexibleSearch.file.FlexibleSearchFileType
import com.intellij.idea.plugin.hybris.groovy.file.GroovyFileToolbarInstaller
import com.intellij.idea.plugin.hybris.impex.file.ImpExFileToolbarInstaller
import com.intellij.idea.plugin.hybris.impex.file.ImpexFileType
import com.intellij.idea.plugin.hybris.polyglotQuery.file.PolyglotQueryFileToolbarInstaller
import com.intellij.idea.plugin.hybris.polyglotQuery.file.PolyglotQueryFileType
import com.intellij.idea.plugin.hybris.settings.components.ProjectSettingsComponent
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.psi.SingleRootFileViewProvider
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.GroovyFileType

class HybrisEditorFactoryListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val project = event.editor.project ?: return
        val file = event.editor.virtualFile ?: return

        if (SingleRootFileViewProvider.isTooLargeForIntelligence(file)) return
        val projectSettings = ProjectSettingsComponent.getInstance(project)
        if (!projectSettings.isHybrisProject()) return

        val toolbarInstaller = when (file.fileType) {
            is FlexibleSearchFileType -> FlexibleSearchFileToolbarInstaller.getInstance()
            is PolyglotQueryFileType -> PolyglotQueryFileToolbarInstaller.getInstance()
            is ImpexFileType -> ImpExFileToolbarInstaller.getInstance()
            is AclFileType -> AclFileToolbarInstaller.getInstance()
            is GroovyFileType -> GroovyFileToolbarInstaller.getInstance()
            else -> null
        } ?: return

        event.editor
            .asSafely<EditorEx>()
            ?.takeIf { it.permanentHeaderComponent == null }
            ?.let { toolbarInstaller.toggleToolbar(project, it) }
    }
}