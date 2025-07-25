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

package com.intellij.idea.plugin.hybris.polyglotQuery.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.idea.plugin.hybris.polyglotQuery.editor.PolyglotQuerySplitEditor
import com.intellij.idea.plugin.hybris.polyglotQuery.psi.PolyglotQueryBindParameter
import com.intellij.idea.plugin.hybris.util.isNotHybrisProject
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class PolyglotQueryParameterInlayHintProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        if (file.isNotHybrisProject) return null

        return FileEditorManager.getInstance(file.project).allEditors
            .filterIsInstance<PolyglotQuerySplitEditor>()
            .find { it.editor == editor }
            ?.let { PolyglotQueryInlayHintsCollector(it) }
    }

    private class PolyglotQueryInlayHintsCollector(private val splitEditor: PolyglotQuerySplitEditor) : SharedBypassCollector {
        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            if (!element.isValid || element.project.isDefault) return
            if (element !is PolyglotQueryBindParameter) return

            splitEditor.takeIf { it.inEditorParameters }
                ?.virtualParameters
                ?.get(element.value)
                ?.takeIf { it.presentationValue.isNotBlank() }
                ?.let {
                    sink.addPresentation(
                        position = InlineInlayPosition(element.textRange.endOffset, true),
                        payloads = null,
                        tooltip = "SQL value: ${it.sqlValue}",
                        hintFormat = HintFormat(HintColorKind.TextWithoutBackground, HintFontSize.ABitSmallerThanInEditor, HintMarginPadding.MarginAndSmallerPadding),
                    ) {
                        text("= ${it.presentationValue}")
                    }
                }
        }
    }
}