/*
 * This file is part of "SAP Commerce Developers Toolset" plugin for Intellij IDEA.
 * Copyright (C) 2019-2023 EPAM Systems <hybrisideaplugin@epam.com> and contributors
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
package com.intellij.idea.plugin.hybris.impex.actions

import com.intellij.idea.plugin.hybris.common.utils.HybrisIcons
import com.intellij.idea.plugin.hybris.impex.psi.ImpexHeaderLine
import com.intellij.idea.plugin.hybris.impex.psi.ImpexUserRights
import com.intellij.idea.plugin.hybris.impex.psi.ImpexValueLine
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class ImpExTableRemoveAction : AbstractImpExTableAction() {

    init {
        with(templatePresentation) {
            text = "Remove Table"
            description = "Remove current table"
            icon = HybrisIcons.TABLE_REMOVE
        }
    }

    override fun performCommand(project: Project, editor: Editor, element: PsiElement) {
        if (element is ImpexUserRights) {
            element.delete()
            return
        }

        val header = when (element) {
            is ImpexHeaderLine -> element
            is ImpexValueLine -> PsiTreeUtil.getPrevSiblingOfType(element, ImpexHeaderLine::class.java)
                ?: return

            else -> return
        }

        val tableRange = header.tableRange

        editor.document.deleteString(tableRange.startOffset, tableRange.endOffset)
    }

    override fun getSuitableElement(element: PsiElement) = PsiTreeUtil
        .getParentOfType(element, ImpexValueLine::class.java, ImpexHeaderLine::class.java, ImpexUserRights::class.java)

    override fun isActionAllowed(project: Project, editor: Editor, element: PsiElement) = getSuitableElement(element) != null

}
