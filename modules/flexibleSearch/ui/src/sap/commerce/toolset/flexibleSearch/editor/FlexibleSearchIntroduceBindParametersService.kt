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

package sap.commerce.toolset.flexibleSearch.editor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.asSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sap.commerce.toolset.flexibleSearch.psi.*
import sap.commerce.toolset.i18n
import sap.commerce.toolset.typeSystem.meta.TSMetaModelStateService

/**
 * Replaces literal values used in the query expressions with named bind parameters and
 * pre-populates the In-Editor Virtual Parameters view with the replaced values.
 */
@Service(Service.Level.PROJECT)
class FlexibleSearchIntroduceBindParametersService(private val project: Project, private val coroutineScope: CoroutineScope) {

    fun introduceBindParameters(fileEditor: FlexibleSearchSplitEditorEx) {
        coroutineScope.launch {
            if (project.isDisposed) return@launch

            val document = fileEditor.editor.document
            val collected = readAction { collectReplacements(document) }
                ?.takeIf { it.replacements.isNotEmpty() }
                ?: return@launch

            writeCommandAction(project, i18n("hybris.fxs.actions.introduce_bind_parameters")) {
                collected.replacements
                    .sortedByDescending { it.range.startOffset }
                    .forEach { document.replaceString(it.range.startOffset, it.range.endOffset, "?${it.parameter.name}") }

                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            val knownParameters = (fileEditor.virtualParameters ?: emptyMap()) + collected.parameters
            val virtualParameters = if (isTypeSystemInitialized()) readAction {
                PsiDocumentManager.getInstance(project).getPsiFile(document)
                    ?.let { PsiTreeUtil.findChildrenOfType(it, FlexibleSearchBindParameter::class.java) }
                    ?.map { FlexibleSearchVirtualParameter.of(it, knownParameters) }
                    ?.distinctBy { it.name }
                    ?.associateBy { it.name }
                    ?: emptyMap()
            } else knownParameters

            withContext(Dispatchers.EDT) {
                fileEditor.virtualParameters = virtualParameters
                fileEditor.inEditorParameters = true
            }
        }
    }

    private fun collectReplacements(document: Document): CollectedReplacements? = PsiDocumentManager.getInstance(project).getPsiFile(document)
        ?.let { CollectedReplacements().apply { collect(it) } }

    private fun isTypeSystemInitialized(): Boolean = !project.isDisposed
        && !DumbService.isDumb(project)
        && TSMetaModelStateService.getInstance(project).initialized()

    private data class Replacement(val range: TextRange, val parameter: FlexibleSearchVirtualParameter)

    private class CollectedReplacements {

        val replacements = mutableListOf<Replacement>()
        val parameters = linkedMapOf<String, FlexibleSearchVirtualParameter>()

        fun collect(psiFile: PsiFile) = PsiTreeUtil.findChildrenOfType(psiFile, FlexibleSearchExpression::class.java)
            .forEach { expression ->
                when (expression) {
                    is FlexibleSearchEquivalenceExpression -> collectOperands(expression.expressionList)
                    is FlexibleSearchComparisonExpression -> collectOperands(expression.expressionList)
                    is FlexibleSearchLikeExpression -> collectOperands(expression.expressionList)
                    is FlexibleSearchBetweenExpression -> collectBetween(expression.expressionList)
                    is FlexibleSearchInExpression -> collectIn(expression)
                    else -> Unit
                }
            }

        private fun collectOperands(operands: List<FlexibleSearchExpression>) {
            val baseName = bindParameterName(operands)

            operands.filterIsInstance<FlexibleSearchLiteralExpression>()
                .forEach { literal -> rawValue(literal)?.let { introduce(baseName, it, literal.textRange) } }
        }

        private fun collectBetween(operands: List<FlexibleSearchExpression>) {
            val baseName = bindParameterName(operands)
            val literals = operands.filterIsInstance<FlexibleSearchLiteralExpression>()
                .mapNotNull { literal -> rawValue(literal)?.let { literal to it } }

            if (literals.size == 2) {
                introduce("${baseName}_from", literals[0].second, literals[0].first.textRange)
                introduce("${baseName}_to", literals[1].second, literals[1].first.textRange)
            } else {
                literals.forEach { (literal, value) -> introduce(baseName, value, literal.textRange) }
            }
        }

        private fun collectIn(expression: FlexibleSearchInExpression) {
            if (expression.selectSubqueryCombinedList.isNotEmpty() || expression.bindParameterList.isNotEmpty()) return

            val operands = expression.expressionList
            val literals = operands.drop(1).filterIsInstance<FlexibleSearchLiteralExpression>()
                .takeIf { it.isNotEmpty() && it.size == operands.size - 1 }
                ?: return
            val values = literals.mapNotNull { rawValue(it) }
                .takeIf { it.size == literals.size }
                ?: return

            introduce(
                baseName = bindParameterName(operands.take(1)),
                rawValue = values.joinToString("\n"),
                range = TextRange(literals.first().textRange.startOffset, literals.last().textRange.endOffset),
                operand = FlexibleSearchTypes.IN_EXPRESSION,
            )
        }

        private fun introduce(baseName: String, rawValue: String, range: TextRange, operand: IElementType? = null) {
            var name = baseName
            var index = 1
            while (parameters[name] != null && parameters.getValue(name).rawValue != rawValue) {
                name = "${baseName}_${++index}"
            }

            val parameter = parameters.getOrPut(name) {
                FlexibleSearchVirtualParameter(name = name, operand = operand).apply { this.rawValue = rawValue }
            }
            replacements += Replacement(range, parameter)
        }

        private fun bindParameterName(operands: List<FlexibleSearchExpression>) = operands.firstNotNullOfOrNull { columnBindParameterName(it) }
            ?: FALLBACK_BASE_NAME

        private fun columnBindParameterName(operand: FlexibleSearchExpression): String? {
            val yColumnRef = operand.asSafely<FlexibleSearchColumnRefYExpression>()
                ?: PsiTreeUtil.findChildOfType(operand, FlexibleSearchColumnRefYExpression::class.java)
            if (yColumnRef != null) return bindParameterName(yColumnRef.selectedTableName?.text, yColumnRef.yColumnName?.text)

            val columnRef = operand.asSafely<FlexibleSearchColumnRefExpression>()
                ?: PsiTreeUtil.findChildOfType(operand, FlexibleSearchColumnRefExpression::class.java)
            if (columnRef != null) return bindParameterName(columnRef.selectedTableName?.text, columnRef.columnName?.text)

            return null
        }

        private fun bindParameterName(tableAlias: String?, columnName: String?): String? = listOfNotNull(tableAlias, columnName)
            .joinToString("_")
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .takeIf { it.isNotBlank() }

        private fun rawValue(literal: FlexibleSearchLiteralExpression): String? = literal.signedNumber?.text
            ?: literal.booleanLiteral?.text
            ?: literal.singleQuoteStringLiteral?.text?.removeSurrounding("'")?.replace("''", "'")
            ?: literal.doubleQuoteStringLiteral?.text?.removeSurrounding("\"")

        companion object {
            private const val FALLBACK_BASE_NAME = "value"
        }
    }

    companion object {
        fun getInstance(project: Project): FlexibleSearchIntroduceBindParametersService = project.service()
    }
}
