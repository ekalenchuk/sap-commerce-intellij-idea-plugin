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

package sap.commerce.toolset.actionSystem

import com.intellij.ide.actions.ToggleToolbarAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorHeaderComponent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.JBIterable
import javax.swing.JComponent

interface HybrisEditorToolbarProvider {

    val toolbarId: String
    val leftGroupId: String
    val rightGroupId: String
    val fileType: FileType

    fun isEnabled(project: Project, vf: VirtualFile): Boolean = vf.fileType == fileType

    /**
     * Toolbar is rendered by the owning editor, it must not be registered as an editor header component:
     * the header is projected anew on each editor binding, which is not supported for the Remote Development.
     */
    fun createToolbar(project: Project, editor: EditorEx): JComponent {
        val actionManager = ActionManager.getInstance()
        val toolbarComponent = EditorHeaderComponent()
        val leftGroup = actionManager.getAction(leftGroupId) as ActionGroup
        val rightGroup = actionManager.getAction(rightGroupId) as ActionGroup
        val leftToolbar = actionManager.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, leftGroup, true)
        val rightToolbar = actionManager.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, rightGroup, true)

        rightToolbar.isReservePlaceAutoPopupIcon = false

        leftToolbar.targetComponent = editor.contentComponent
        rightToolbar.targetComponent = editor.contentComponent

        toolbarComponent.add(leftToolbar.component, "Center")
        toolbarComponent.add(rightToolbar.component, "East")

        leftToolbar.updateActionsAsync()

        ToggleToolbarAction.setToolbarVisible(
            toolbarId,
            PropertiesComponent.getInstance(project),
            JBIterable.of(toolbarComponent),
            null as Boolean?
        )

        return toolbarComponent
    }

    companion object {
        val EP = ExtensionPointName.create<HybrisEditorToolbarProvider>("sap.commerce.toolset.editor.toolbarProvider")
    }

}
