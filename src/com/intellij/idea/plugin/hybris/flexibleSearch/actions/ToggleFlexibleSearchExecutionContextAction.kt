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

package com.intellij.idea.plugin.hybris.flexibleSearch.actions

import com.intellij.idea.plugin.hybris.common.utils.HybrisIcons
import com.intellij.idea.plugin.hybris.flexibleSearch.editor.flexibleSearchSplitEditor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ToolbarUtils
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbar
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.awt.Dimension
import javax.swing.JButton

class ToggleFlexibleSearchExecutionContextAction : AnAction(
    "Show Context Panel",
    "Show Context Panel",
    HybrisIcons.FlexibleSearch.Actions.TOGGLE_CONTEXT_PANEL
), DumbAware {


    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val flexibleSearchSplitEditor = e.flexibleSearchSplitEditor() ?: return

        MyFloatingToolbarPopup.show(project, e)
    }

}

object MyFloatingToolbarPopup {

    fun show(project: Project, event: AnActionEvent) {

        val defineUserAction = ActionManager.getInstance().getAction("hybris.fxs.user") as ActionGroup

//        val toolbarPanel = JPanel(FlowLayout()).apply {
//
//        }
        val flexibleSearchSplitEditor = event.flexibleSearchSplitEditor() ?: return
        val toolbar2 = ToolbarUtils.createImmediatelyUpdatedToolbar(defineUserAction, event.place, flexibleSearchSplitEditor.component, true, {})

        val toolbarPanel = FloatingToolbar(flexibleSearchSplitEditor.component, defineUserAction, flexibleSearchSplitEditor)
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(toolbarPanel, null)
            .setRequestFocus(true)
            .setMovable(true)
            .setResizable(false)
            .setCancelOnClickOutside(true)
            .createPopup().apply {
                size = Dimension(100, 30)
            }

        popup.showInBestPositionFor(event.dataContext)
    }

    private fun makeButton(text: String, onClick: () -> Unit): JButton {
        return JButton(text).apply {
            addActionListener { onClick() }
        }
    }
}