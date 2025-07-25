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

import com.intellij.idea.plugin.hybris.flexibleSearch.editor.flexibleSearchSplitEditor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.components.JBTextField
import com.intellij.util.asSafely
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBDimension
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import javax.swing.JPanel

class FlexibleSearchSetupCustomUserAction : AnAction(
    "Enter..."
), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {

        val project = e.project ?: return
        val flexibleSearchSplitEditor = e.flexibleSearchSplitEditor() ?: return
        val currentContextUser = flexibleSearchSplitEditor.user

        val popup = createPopup(project, currentContextUser)
        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                if (event.isOk) {
                    val user = event.asPopup().content.components.firstOrNull()
                        ?.asSafely<JPanel>()
                        ?.components
                        ?.firstOrNull()
                        ?.asSafely<TextFieldWithCompletion>()
                        ?.text
                        ?.takeIf { it.isNotBlank() }
                        ?: "admin"
                    flexibleSearchSplitEditor.user = user
                } else {
                }
            }
        })
        val event = e.inputEvent.asSafely<MouseEvent>() ?: return
        popup.showUnderneathOf(e.inputEvent?.component ?: return)
    }

    fun createPopup(project: Project, currentContextUser: String): JBPopup {
        val panel = JPanel(BorderLayout())
        val myTextField = JBTextField()
        myTextField.text = currentContextUser

        panel.add(myTextField, "Center")
        val builder = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, myTextField)
            .setCancelOnClickOutside(true)
            .setAdText("Enter a user code")
            .setRequestFocus(true)
            .setResizable(true)
            .setMayBeParent(true)
        val popup = builder.createPopup()
        popup.setMinimumSize(JBDimension(200, 90))

        val okAction: AnAction = object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) {
                this.unregisterCustomShortcutSet(popup.content)
                val enteredText = myTextField.text
                println(enteredText)
                val inputEvent = e.inputEvent
                popup.closeOk(inputEvent)
            }
        }
        okAction.registerCustomShortcutSet(CommonShortcuts.getCtrlEnter(), popup.content)
        return popup
    }

}