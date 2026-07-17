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

package sap.commerce.toolset.impex.editor

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorUtil
import com.intellij.codeInspection.QuickFix
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.icons.AllIcons
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import com.intellij.ui.ColorUtil
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorTextField
import com.intellij.ui.ExpandableEditorSupport
import com.intellij.ui.InlineBanner
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.table.JBTable
import com.intellij.util.asSafely
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sap.commerce.toolset.HybrisIcons
import sap.commerce.toolset.i18n
import sap.commerce.toolset.impex.ImpExLanguage
import sap.commerce.toolset.impex.constants.modifier.AttributeModifier
import sap.commerce.toolset.impex.constants.modifier.TypeModifier
import sap.commerce.toolset.impex.lang.folding.ImpExFoldingPlaceholderBuilder
import sap.commerce.toolset.impex.lang.folding.ImpExFoldingPlaceholderBuilderFactory
import sap.commerce.toolset.impex.psi.ImpExFile
import sap.commerce.toolset.impex.psi.ImpExFullHeaderParameter
import sap.commerce.toolset.impex.psi.ImpExHeaderLine
import sap.commerce.toolset.impex.psi.ImpExMacroDeclaration
import sap.commerce.toolset.impex.psi.ImpExSubTypeName
import sap.commerce.toolset.impex.psi.ImpExValueGroup
import sap.commerce.toolset.impex.psi.ImpExValueLine
import sap.commerce.toolset.psi.navigate
import sap.commerce.toolset.typeSystem.codeInsight.completion.TSCompletionService
import sap.commerce.toolset.typeSystem.meta.TSMetaModelAccess
import sap.commerce.toolset.typeSystem.meta.model.TSMetaType
import sap.commerce.toolset.typeSystem.meta.model.TSGlobalMetaEnum
import sap.commerce.toolset.typeSystem.meta.model.TSGlobalMetaItem
import sap.commerce.toolset.typeSystem.meta.model.TSGlobalMetaRelation
import sap.commerce.toolset.ui.scrollPanel
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultCellEditor
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.JTableHeader
import javax.swing.table.TableCellRenderer
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
class ImpExInEditorTableView(private val project: Project, private val coroutineScope: CoroutineScope) {

    fun renderTableView(fileEditor: ImpExSplitEditorEx) {
        coroutineScope.launch {
            if (project.isDisposed) return@launch

            val problems = collectProblems(fileEditor.editor.document)
            val tableViewModels = readAction { collectTableViewModels(fileEditor.editor.document, problems) }

            withContext(Dispatchers.EDT) {
                if (project.isDisposed || !fileEditor.inEditorTableView) return@withContext

                if (isEditingInProgress(fileEditor)) {
                    fileEditor.refreshTableView(1000.milliseconds)
                    return@withContext
                }

                installRefreshOnDocumentChange(fileEditor)

                fileEditor.showTableView(renderRootPanel(tableViewModels, fileEditor))
            }
        }
    }

    private fun isEditingInProgress(fileEditor: ImpExSplitEditorEx) = fileEditor.tableViewComponent
        ?.let { UIUtil.uiTraverser(it).filter(JTable::class.java).filter { table -> table.isEditing }.isNotEmpty }
        ?: false

    private fun installRefreshOnDocumentChange(fileEditor: ImpExSplitEditorEx) {
        if (fileEditor.inEditorTableViewDisposable != null) return

        val disposable = Disposer.newDisposable("ImpExInEditorTableView").also {
            fileEditor.inEditorTableViewDisposable = it
            Disposer.register(fileEditor.textEditor, it)
        }

        fileEditor.editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = fileEditor.refreshTableView()
        }, disposable)
    }

    // Problems

    private suspend fun collectProblems(document: Document): Map<PsiElement, List<CellProblem>> = coroutineToIndicator {
        runReadAction {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
                ?.asSafely<ImpExFile>()
                ?: return@runReadAction emptyMap()

            val problems = mutableMapOf<PsiElement, MutableList<CellProblem>>()

            PsiTreeUtil.collectElementsOfType(psiFile, PsiErrorElement::class.java)
                .forEach { registerProblem(problems, it, CellProblem(it.errorDescription, error = true)) }

            if (!DumbService.isDumb(project)) collectInspectionProblems(psiFile, problems)

            problems
        }
    }

    private fun collectInspectionProblems(psiFile: ImpExFile, problems: MutableMap<PsiElement, MutableList<CellProblem>>) {
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        val inspectionManager = InspectionManager.getInstance(project)

        profile.getInspectionTools(psiFile)
            .filterIsInstance<LocalInspectionToolWrapper>()
            .mapNotNull { wrapper -> wrapper.displayKey?.let { wrapper to it } }
            .filter { (_, displayKey) -> profile.isToolEnabled(displayKey, psiFile) }
            .filter { (wrapper, _) -> wrapper.language == null || ImpExLanguage.id.equals(wrapper.language, true) }
            .forEach { (wrapper, displayKey) ->
                val severity = profile.getErrorLevel(displayKey, psiFile).severity

                wrapper.tool.processFile(psiFile, inspectionManager).forEach { descriptor ->
                    val element = descriptor.psiElement ?: return@forEach
                    val message = ProblemDescriptorUtil.renderDescriptionMessage(descriptor, element)
                    val fixes = descriptor.fixes
                        ?.map { ProblemFixModel(it.name, descriptor, it) }
                        ?: emptyList()

                    registerProblem(problems, element, CellProblem(message, error = severity >= HighlightSeverity.ERROR, fixes = fixes))
                }
            }
    }

    private fun registerProblem(problems: MutableMap<PsiElement, MutableList<CellProblem>>, element: PsiElement, problem: CellProblem) {
        val anchor = element.parentOfType<ImpExValueGroup>(true)
            ?: element.parentOfType<ImpExFullHeaderParameter>(true)
            ?: element.parentOfType<ImpExSubTypeName>(true)
            ?: element.parentOfType<ImpExMacroDeclaration>(true)
            ?: element.parentOfType<ImpExHeaderLine>(true)
            ?: return

        problems.getOrPut(anchor) { mutableListOf() }.add(problem)
    }

    // Model collection

    private fun collectTableViewModels(document: Document, problems: Map<PsiElement, List<CellProblem>>): List<TableViewModel> {
        val impexFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
            ?.asSafely<ImpExFile>()
            ?: return emptyList()
        val placeholderBuilder = ImpExFoldingPlaceholderBuilderFactory.getPlaceholderBuilder()

        val statementModels = impexFile.getHeaderLines()
            .map { (headerLine, valueLines) -> statementTableViewModel(document, placeholderBuilder, headerLine, valueLines, problems) }
        val headerLineOffsets = impexFile.getHeaderLines().keys
            .map { it.startOffset }
            .sorted()
        val macroGroups = PsiTreeUtil.findChildrenOfType(impexFile, ImpExMacroDeclaration::class.java)
            .groupBy { declaration -> headerLineOffsets.count { it < declaration.startOffset } }
            .mapValues { (_, declarations) -> macrosTableViewModel(document, declarations, problems) }

        return buildList {
            statementModels.forEachIndexed { index, model ->
                macroGroups[index]?.let { add(it) }
                add(model)
            }
            macroGroups[statementModels.size]?.let { add(it) }
        }
    }

    private fun statementTableViewModel(
        document: Document,
        placeholderBuilder: ImpExFoldingPlaceholderBuilder,
        headerLine: ImpExHeaderLine,
        valueLines: Collection<ImpExValueLine>,
        problems: Map<PsiElement, List<CellProblem>>,
    ): TableViewModel {
        val headerParameters = headerLine.fullHeaderParameterList
        val hasSubType = valueLines.any { it.subTypeName != null }

        val columns = buildList {
            add(ColumnModel(COLUMN_LINE_NUMBER, null, kind = ColumnKind.LINE_NUMBER))
            if (hasSubType) add(ColumnModel(i18n("hybris.impex.table_view.column.subtype"), null, kind = ColumnKind.SUB_TYPE, editable = true))
            headerParameters.forEachIndexed { index, parameter ->
                val typeMeta = columnTypeMeta(parameter)

                add(
                    ColumnModel(
                        name = headerParameterPresentation(placeholderBuilder, parameter),
                        tooltip = parameter.text.trim(),
                        kind = ColumnKind.PARAMETER,
                        unique = parameter.isUnique,
                        editable = true,
                        parameterIndex = index,
                        variants = typeMeta.variants,
                        reference = typeMeta.reference,
                        problem = problems[parameter]?.merge(),
                    )
                )
            }
        }

        val parameterColumns = columns.filter { it.kind == ColumnKind.PARAMETER }
        val rows = valueLines.map { valueLine ->
            val cells = buildList {
                val lineNumber = document.getLineNumber(valueLine.textRange.startOffset) + 1
                add(CellModel(lineNumber.toString(), muted = true, alignRight = true))
                if (hasSubType) {
                    val subTypeName = valueLine.subTypeName
                    val subType = subTypeName?.text.orEmpty()
                    add(CellModel(subType, rawText = subType, problem = subTypeName?.let { problems[it] }?.merge()))
                }
                headerParameters.indices.forEach { index ->
                    add(cellModel(valueLine.getValueGroup(index), parameterColumns.getOrNull(index)?.reference == true, problems))
                }
            }

            RowModel(cells.toMutableList(), valueLinePointer = SmartPointerManager.createPointer(valueLine))
        }

        return TableViewModel(
            title = headerLine.anyHeaderMode.text.trim(),
            typeName = headerLine.fullHeaderType?.headerTypeName?.text?.trim(),
            typeModifiers = headerLine.fullHeaderType?.modifiers
                ?.attributeList
                ?.map { placeholderBuilder.getPlaceholder(it).trim() }
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(", ", "[", "]"),
            titleProblem = problems[headerLine]?.merge(),
            headerLinePointer = SmartPointerManager.createPointer(headerLine),
            parameterCount = headerParameters.size,
            columns = columns,
            rows = rows,
        )
    }

    private fun macrosTableViewModel(
        document: Document,
        declarations: List<ImpExMacroDeclaration>,
        problems: Map<PsiElement, List<CellProblem>>,
    ): TableViewModel {
        val columns = listOf(
            ColumnModel(COLUMN_LINE_NUMBER, null, kind = ColumnKind.LINE_NUMBER),
            ColumnModel(i18n("hybris.impex.table_view.column.macro.key"), null, kind = ColumnKind.MACRO_KEY, unique = true),
            ColumnModel(i18n("hybris.impex.table_view.column.macro.value"), null, kind = ColumnKind.MACRO_VALUE, editable = true),
        )

        val rows = declarations.map { declaration ->
            val rawValue = declaration.macroValuesDec?.text?.trim().orEmpty()
            val resolvedValue = try {
                declaration.macroNameDec.resolveValue(HashSet()).trim()
            } catch (_: IndexNotReadyException) {
                rawValue
            }
            val problem = problems[declaration]?.merge()
            val lineNumber = document.getLineNumber(declaration.textRange.startOffset) + 1

            RowModel(
                cells = mutableListOf(
                    CellModel(lineNumber.toString(), muted = true, alignRight = true),
                    CellModel(declaration.macroNameDec.text.trim(), problem = problem),
                    CellModel(
                        text = resolvedValue,
                        rawText = rawValue,
                        tooltip = rawValue.takeIf { it != resolvedValue },
                        substituted = rawValue.isNotEmpty() && rawValue != resolvedValue,
                    ),
                ),
                macroDeclarationPointer = SmartPointerManager.createPointer(declaration),
            )
        }

        return TableViewModel(
            title = i18n("hybris.impex.table_view.macros"),
            typeName = null,
            typeModifiers = null,
            titleProblem = null,
            headerLinePointer = null,
            parameterCount = 0,
            columns = columns,
            rows = rows,
        )
    }

    private fun columnTypeMeta(parameter: ImpExFullHeaderParameter): ColumnTypeMeta {
        if (DumbService.isDumb(project)) return ColumnTypeMeta()

        val attributeType = try {
            parameter.typeSystemContext?.attributeType
        } catch (_: IndexNotReadyException) {
            null
        } ?: return ColumnTypeMeta()

        if (BOOLEAN_TYPES.any { it.equals(attributeType, true) }) return ColumnTypeMeta(variants = listOf("true", "false"))

        return try {
            when (val classifier = TSMetaModelAccess.getInstance(project).findMetaClassifierByName(attributeType)) {
                is TSGlobalMetaEnum -> ColumnTypeMeta(variants = classifier.values.keys.sorted())
                is TSGlobalMetaItem, is TSGlobalMetaRelation -> ColumnTypeMeta(reference = true)
                else -> ColumnTypeMeta()
            }
        } catch (_: IndexNotReadyException) {
            ColumnTypeMeta()
        }
    }

    private fun headerParameterPresentation(placeholderBuilder: ImpExFoldingPlaceholderBuilder, parameter: ImpExFullHeaderParameter) = buildString {
        append(parameter.anyHeaderParameterName.text.trim())

        parameter.parametersList
            .forEach { append(placeholderBuilder.getPlaceholder(it)) }

        parameter.modifiersList
            .flatMap { it.attributeList }
            .map { placeholderBuilder.getPlaceholder(it).trim() }
            .filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ", " [", "]")
            ?.let { append(it) }
    }

    private fun cellModel(valueGroup: ImpExValueGroup?, reference: Boolean, problems: Map<PsiElement, List<CellProblem>>): CellModel {
        valueGroup ?: return CellModel("", muted = true)

        val rawValue = valueGroup.value?.text?.trim()
        val resolvedValue = try {
            valueGroup.resolveValue()?.trim()
        } catch (_: IndexNotReadyException) {
            rawValue
        }
        val text = (resolvedValue ?: rawValue.orEmpty()).collapseLines()

        return CellModel(
            text = text,
            rawText = rawValue.orEmpty(),
            tooltip = rawValue?.takeIf { it != resolvedValue },
            muted = rawValue == null && !resolvedValue.isNullOrEmpty(),
            substituted = rawValue != null && resolvedValue != null && rawValue != resolvedValue,
            valueKind = valueKind(rawValue, text, reference),
            problem = problems[valueGroup]?.merge(),
        )
    }

    private fun valueKind(rawValue: String?, text: String, reference: Boolean) = when {
        rawValue != null && rawValue.length >= 2 && (rawValue.first() == '"' || rawValue.first() == '\'') && rawValue.last() == rawValue.first() -> ValueKind.STRING
        text.isNotEmpty() && text.toDoubleOrNull() != null -> ValueKind.NUMBER
        reference && text.isNotBlank() -> ValueKind.REFERENCE
        else -> ValueKind.DEFAULT
    }

    private fun String.collapseLines() = replace(LINE_CONTINUATION_REGEX, " ")

    private fun List<CellProblem>.merge() = CellProblem(
        message = joinToString("\n") { it.message },
        error = any { it.error },
        fixes = flatMap { it.fixes },
    )

    // Navigation

    private fun navigateFromColumnHeader(
        fileEditor: ImpExSplitEditorEx,
        tableViewModel: TableViewModel,
        columnIndex: Int,
        point: RelativePoint,
        search: TableViewSearch,
    ) {
        val column = tableViewModel.columns.getOrNull(columnIndex) ?: return
        if (column.kind != ColumnKind.PARAMETER || column.parameterIndex == null) return

        navigateToTargets(fileEditor, point, search) {
            tableViewModel.headerLinePointer?.element
                ?.getFullHeaderParameter(column.parameterIndex)
                ?.let { referenceTargets(it) }
                ?: emptyList()
        }
    }

    private fun navigateFromTitle(fileEditor: ImpExSplitEditorEx, tableViewModel: TableViewModel, point: RelativePoint, search: TableViewSearch) {
        tableViewModel.headerLinePointer ?: return

        navigateToTargets(fileEditor, point, search) {
            tableViewModel.headerLinePointer.element
                ?.fullHeaderType
                ?.headerTypeName
                ?.let { referenceTargets(it) }
                ?: emptyList()
        }
    }

    private fun navigateFromCell(
        fileEditor: ImpExSplitEditorEx,
        tableViewModel: TableViewModel,
        rowIndex: Int,
        columnIndex: Int,
        point: RelativePoint,
        search: TableViewSearch,
    ) {
        val row = tableViewModel.rows.getOrNull(rowIndex) ?: return
        val column = tableViewModel.columns.getOrNull(columnIndex) ?: return

        navigateToTargets(fileEditor, point, search) {
            when (column.kind) {
                ColumnKind.SUB_TYPE -> row.valueLinePointer?.element
                    ?.subTypeName
                    ?.let { referenceTargets(it) }

                ColumnKind.PARAMETER -> column.parameterIndex
                    ?.let { row.valueLinePointer?.element?.getValueGroup(it) }
                    ?.value
                    ?.let { referenceTargets(it) }

                ColumnKind.MACRO_KEY -> row.macroDeclarationPointer?.element
                    ?.let { listOf(it) }

                ColumnKind.MACRO_VALUE -> row.macroDeclarationPointer?.element
                    ?.macroValuesDec
                    ?.let { referenceTargets(it) }

                else -> null
            }
                ?: emptyList()
        }
    }

    private fun referenceTargets(element: PsiElement): List<PsiElement> = SyntaxTraverser.psiTraverser(element)
        .flatMap { it.references.toList() }
        .flatMap { reference ->
            reference.asSafely<PsiPolyVariantReference>()
                ?.multiResolve(false)
                ?.mapNotNull { it.element }
                ?: listOfNotNull(reference.resolve())
        }
        .distinct()

    private fun navigateToTargets(fileEditor: ImpExSplitEditorEx, point: RelativePoint, search: TableViewSearch, targetsProvider: () -> List<PsiElement>) {
        coroutineScope.launch {
            if (project.isDisposed) return@launch

            val targets = readAction {
                try {
                    targetsProvider().map { NavigationTarget(targetPresentation(it), SmartPointerManager.createPointer(it)) }
                } catch (_: IndexNotReadyException) {
                    emptyList()
                }
            }

            withContext(Dispatchers.EDT) {
                when {
                    targets.isEmpty() -> Unit
                    targets.size == 1 -> navigateToTarget(fileEditor, search, targets.first())
                    else -> JBPopupFactory.getInstance()
                        .createPopupChooserBuilder(targets)
                        .setTitle(i18n("hybris.impex.table_view.navigate.title"))
                        .setRenderer(SimpleListCellRenderer.create("") { it.label })
                        .setItemChosenCallback { navigateToTarget(fileEditor, search, it) }
                        .createPopup()
                        .show(point)
                }
            }
        }
    }

    private fun targetPresentation(target: PsiElement): String {
        val name = target.asSafely<PsiNamedElement>()?.name
            ?: target.text?.take(50)
            ?: target.toString()
        val fileName = target.containingFile?.name

        return if (fileName != null) "$name  ($fileName)" else name
    }

    private fun navigateToTarget(fileEditor: ImpExSplitEditorEx, search: TableViewSearch, target: NavigationTarget) {
        val element = target.pointer.element ?: return
        val sameFile = element.containingFile?.virtualFile == fileEditor.file

        if (sameFile && search.selectElement(element)) return
        if (sameFile) fileEditor.inEditorTableView = false

        navigate(element)
    }

    private fun isNavigationClick(e: MouseEvent) = e.button == MouseEvent.BUTTON1
        && (e.isMetaDown || e.isControlDown)

    private fun isNavigationHover(e: MouseEvent) = e.isMetaDown || e.isControlDown

    private fun isNavigatableCell(tableViewModel: TableViewModel, rowIndex: Int, columnIndex: Int): Boolean {
        val column = tableViewModel.columns.getOrNull(columnIndex) ?: return false
        val cell = tableViewModel.rows.getOrNull(rowIndex)?.cells?.getOrNull(columnIndex) ?: return false

        return when (column.kind) {
            ColumnKind.PARAMETER, ColumnKind.SUB_TYPE -> cell.rawText.isNotBlank()
            ColumnKind.MACRO_KEY -> true
            ColumnKind.MACRO_VALUE -> cell.rawText.contains('$')
            else -> false
        }
    }

    // Rendering

    private fun renderRootPanel(tableViewModels: List<TableViewModel>, fileEditor: ImpExSplitEditorEx): JComponent {
        val search = TableViewSearch()

        val content = JPanel(VerticalLayout(JBUI.scale(16))).apply {
            border = JBUI.Borders.empty(12, 16, 16, 16)
        }

        if (tableViewModels.isEmpty()) content.add(noTablesBanner())
        else tableViewModels.forEach { content.add(renderTablePanel(it, fileEditor, search)) }

        val rootPanel = object : JPanel(BorderLayout()), UiDataProvider {
            override fun uiDataSnapshot(sink: DataSink) {
                sink[CommonDataKeys.PROJECT] = project
                sink[PlatformDataKeys.FILE_EDITOR] = fileEditor
                sink[CommonDataKeys.EDITOR] = fileEditor.editor
                sink[CommonDataKeys.LANGUAGE] = ImpExLanguage
                fileEditor.file?.let {
                    sink[CommonDataKeys.VIRTUAL_FILE] = it
                    sink[CommonDataKeys.VIRTUAL_FILE_ARRAY] = arrayOf(it)
                }
            }
        }

        return rootPanel.apply {
            add(renderHeaderPanel(fileEditor, this, search), BorderLayout.NORTH)
            add(scrollPanel(content), BorderLayout.CENTER)

            registerKeyboardAction(
                { fileEditor.inEditorTableView = false },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
            )
        }
    }

    private fun noTablesBanner() = InlineBanner(
        i18n("hybris.impex.table_view.no_tables"),
        EditorNotificationPanel.Status.Info
    ).showCloseButton(false)

    private fun renderHeaderPanel(fileEditor: ImpExSplitEditorEx, targetComponent: JComponent, search: TableViewSearch): JComponent {
        val actionManager = ActionManager.getInstance()

        val showSourceAction = object : DumbAwareAction(
            i18n("hybris.impex.table_view.show_source"),
            i18n("hybris.impex.table_view.show_source.description"),
            AllIcons.Actions.EditSource
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                fileEditor.inEditorTableView = false
            }
        }
        val refreshAction = object : DumbAwareAction(
            i18n("hybris.impex.table_view.refresh"),
            i18n("hybris.impex.table_view.refresh.description"),
            HybrisIcons.Actions.REFRESH
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                renderTableView(fileEditor)
            }
        }

        val actionGroup = DefaultActionGroup().apply {
            actionManager.getAction("hybris.impex.executionContextSettings")?.let { add(it) }
            addSeparator()
            actionManager.getAction("hybris.impex.import")?.let { add(it) }
            actionManager.getAction("hybris.impex.openQuery")?.let { add(it) }
            actionManager.getAction("hybris.impex.validate")?.let { add(it) }
            addSeparator()
            add(refreshAction)
            add(showSourceAction)
        }

        val toolbar = actionManager.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup, true)
            .apply { this.targetComponent = targetComponent }

        val searchField = renderSearchField(search, fileEditor)
        val replacePanel = renderReplacePanel(search, fileEditor)

        val focusSearchAction = DumbAwareAction.create { searchField.requestFocusInWindow() }
        actionManager.getAction(IdeActions.ACTION_FIND)
            ?.shortcutSet
            ?.let { focusSearchAction.registerCustomShortcutSet(it, targetComponent) }
            ?: focusSearchAction.registerCustomShortcutSet(
                CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)),
                targetComponent,
            )

        val toggleReplaceAction = DumbAwareAction.create {
            fileEditor.putUserData(KEY_TABLE_VIEW_REPLACE_VISIBLE, true)
            replacePanel.isVisible = true
            searchField.requestFocusInWindow()
        }
        actionManager.getAction(IdeActions.ACTION_REPLACE)
            ?.shortcutSet
            ?.let { toggleReplaceAction.registerCustomShortcutSet(it, targetComponent) }
            ?: toggleReplaceAction.registerCustomShortcutSet(
                CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_R, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)),
                targetComponent,
            )

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLineBottom(JBColor.border())

            add(toolbar.component, BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply {
                add(searchField, BorderLayout.NORTH)
                add(replacePanel, BorderLayout.SOUTH)
            }, BorderLayout.EAST)
        }
    }

    private fun renderSearchField(search: TableViewSearch, fileEditor: ImpExSplitEditorEx) = SearchTextField(false).apply {
        textEditor.emptyText.text = i18n("hybris.impex.table_view.search")
        textEditor.columns = 20

        addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) = onQueryChanged()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) = onQueryChanged()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) = onQueryChanged()

            private fun onQueryChanged() {
                fileEditor.putUserData(KEY_TABLE_VIEW_SEARCH_QUERY, text)
                search.update(text)
            }
        })

        textEditor.registerKeyboardAction(
            { search.next() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_FOCUSED,
        )
        textEditor.registerKeyboardAction(
            { search.previous() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK),
            JComponent.WHEN_FOCUSED,
        )
        textEditor.registerKeyboardAction(
            {
                if (text.isNotEmpty()) text = ""
                else transferFocus()
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_FOCUSED,
        )

        fileEditor.getUserData(KEY_TABLE_VIEW_SEARCH_QUERY)
            ?.takeIf { it.isNotEmpty() }
            ?.let { text = it }
    }

    private fun renderReplacePanel(search: TableViewSearch, fileEditor: ImpExSplitEditorEx): JComponent {
        val replaceField = JBTextField(16).apply {
            emptyText.text = i18n("hybris.impex.table_view.replace.placeholder")
            text = fileEditor.getUserData(KEY_TABLE_VIEW_REPLACE_TEXT).orEmpty()

            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = store()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = store()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent) = store()

                private fun store() = fileEditor.putUserData(KEY_TABLE_VIEW_REPLACE_TEXT, text)
            })
        }
        val replaceButton = JButton(i18n("hybris.impex.table_view.replace")).apply {
            addActionListener { replaceCurrentMatch(search, replaceField.text) }
        }
        val replaceAllButton = JButton(i18n("hybris.impex.table_view.replace_all")).apply {
            addActionListener { replaceAllMatches(search, replaceField.text) }
        }

        replaceField.registerKeyboardAction(
            { replaceCurrentMatch(search, replaceField.text) },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_FOCUSED,
        )

        return JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
            isVisible = fileEditor.getUserData(KEY_TABLE_VIEW_REPLACE_VISIBLE) == true

            add(replaceField)
            add(replaceButton)
            add(replaceAllButton)
        }
    }

    private fun replaceCurrentMatch(search: TableViewSearch, replacement: String) {
        if (search.query.isEmpty()) return

        search.currentMatch()?.let { replaceMatch(it, search.query, replacement) }
    }

    private fun replaceAllMatches(search: TableViewSearch, replacement: String) {
        if (search.query.isEmpty()) return

        search.allMatches()
            .sortedWith(compareByDescending<SearchMatch> { matchOffset(it) }.thenByDescending { it.columnIndex })
            .forEach { match ->
                replaceMatch(match, search.query, replacement)
                PsiDocumentManager.getInstance(project).commitAllDocuments()
            }
    }

    private fun matchOffset(match: SearchMatch): Int {
        val row = match.tableViewModel.rows.getOrNull(match.rowIndex) ?: return 0

        return row.valueLinePointer?.element?.textRange?.startOffset
            ?: row.macroDeclarationPointer?.element?.textRange?.startOffset
            ?: 0
    }

    private fun replaceMatch(match: SearchMatch, query: String, replacement: String): Boolean {
        val column = match.tableViewModel.columns.getOrNull(match.columnIndex) ?: return false
        val row = match.tableViewModel.rows.getOrNull(match.rowIndex) ?: return false
        val rawText = row.cells.getOrNull(match.columnIndex)?.rawText ?: return false

        if (rawText.isEmpty() || !rawText.contains(query, true)) return false

        val newText = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
            .replace(rawText, Regex.escapeReplacement(replacement))

        when (column.kind) {
            ColumnKind.MACRO_VALUE -> commitMacroEdit(row, newText)
            ColumnKind.SUB_TYPE, ColumnKind.PARAMETER -> commitCellEdit(row, column, newText)
            else -> return false
        }

        return true
    }

    private fun renderTablePanel(tableViewModel: TableViewModel, fileEditor: ImpExSplitEditorEx, search: TableViewSearch): JComponent {
        val (tableComponent, table) = renderTable(tableViewModel, fileEditor, search)

        return JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            isOpaque = false

            add(renderTitlePanel(tableViewModel, fileEditor, this, search, table), BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(tableComponent)
            }, BorderLayout.CENTER)
        }
    }

    private fun renderTitlePanel(
        tableViewModel: TableViewModel,
        fileEditor: ImpExSplitEditorEx,
        targetComponent: JComponent,
        search: TableViewSearch,
        table: JBTable,
    ): JComponent {
        val title = renderTitle(tableViewModel, fileEditor, search)

        if (tableViewModel.headerLinePointer == null) return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(title, BorderLayout.WEST)
        }

        val addRowAction = object : DumbAwareAction(
            i18n("hybris.impex.table_view.add_row"),
            i18n("hybris.impex.table_view.add_row.description"),
            AllIcons.General.Add
        ) {
            override fun actionPerformed(e: AnActionEvent) = addRow(tableViewModel)
        }
        val addColumnAction = object : DumbAwareAction(
            i18n("hybris.impex.table_view.add_column"),
            i18n("hybris.impex.table_view.add_column.description"),
            HybrisIcons.ImpEx.Actions.INSERT_COLUMN_RIGHT
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                val lastColumn = table.columnCount - 1
                val anchor = if (lastColumn >= 0) table.tableHeader.getHeaderRect(lastColumn).let { Rectangle(it.x + it.width, it.y, it.width, it.height) }
                else Rectangle(0, 0, 0, 0)

                showInlineHeaderEditor(table.tableHeader, anchor, "", { attributeCompletions(tableViewModel) }) { text ->
                    insertColumnAt(tableViewModel, tableViewModel.parameterCount, text)
                }
            }
        }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, DefaultActionGroup(addRowAction, addColumnAction), true)
            .apply {
                this.targetComponent = targetComponent
                component.isOpaque = false
            }

        return JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = false

            add(title, BorderLayout.WEST)
            add(toolbar.component, BorderLayout.CENTER)
        }
    }

    private fun renderTitle(tableViewModel: TableViewModel, fileEditor: ImpExSplitEditorEx, search: TableViewSearch) = SimpleColoredComponent().apply {
        isOpaque = false

        append(tableViewModel.title, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, modeColor(tableViewModel.title)))
        tableViewModel.typeName?.let { append(" $it", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES) }
        tableViewModel.typeModifiers?.let { append(" $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }

        val rowsSuffix = if (tableViewModel.rows.size == 1) i18n("hybris.impex.table_view.row") else i18n("hybris.impex.table_view.rows")
        append("  ${tableViewModel.rows.size} $rowsSuffix", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)

        tableViewModel.titleProblem?.let { append("  ${it.message}", SimpleTextAttributes.ERROR_ATTRIBUTES) }

        if (tableViewModel.typeName != null) {
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    when {
                        isNavigationClick(e) -> navigateFromTitle(fileEditor, tableViewModel, RelativePoint(e), search)
                        e.button == MouseEvent.BUTTON1 && e.clickCount == 2 -> editTypeInline(this@apply, tableViewModel)
                    }
                }

                override fun mouseExited(e: MouseEvent) {
                    cursor = Cursor.getDefaultCursor()
                }
            })
            addMouseMotionListener(object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    cursor = if (isNavigationHover(e)) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
                }
            })
        }
    }

    private fun modeColor(mode: String) = when {
        mode.contains("REMOVE") -> MODE_REMOVE_COLOR
        mode.contains("INSERT_UPDATE") -> MODE_INSERT_UPDATE_COLOR
        mode.contains("INSERT") -> MODE_INSERT_COLOR
        mode.contains("UPDATE") -> MODE_UPDATE_COLOR
        else -> UIUtil.getLabelForeground()
    }

    private fun renderTable(tableViewModel: TableViewModel, fileEditor: ImpExSplitEditorEx, search: TableViewSearch): Pair<JComponent, JBTable> {
        val tableModel = object : AbstractTableModel() {
            override fun getRowCount() = tableViewModel.rows.size
            override fun getColumnCount() = tableViewModel.columns.size
            override fun getColumnName(column: Int) = tableViewModel.columns[column].name
            override fun getValueAt(rowIndex: Int, columnIndex: Int) = tableViewModel.rows[rowIndex].cells[columnIndex].text
            override fun isCellEditable(rowIndex: Int, columnIndex: Int) = tableViewModel.columns[columnIndex].editable

            override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
                val newValue = aValue?.toString() ?: return
                val row = tableViewModel.rows.getOrNull(rowIndex) ?: return
                val column = tableViewModel.columns.getOrNull(columnIndex) ?: return
                val text = newValue.trim()

                if (text == row.cells[columnIndex].rawText) return

                row.cells[columnIndex] = row.cells[columnIndex].copy(
                    text = text.collapseLines(),
                    rawText = text,
                    tooltip = null,
                    muted = false,
                    substituted = false,
                    valueKind = valueKind(text, text.collapseLines(), column.reference),
                )
                fireTableCellUpdated(rowIndex, columnIndex)

                when (column.kind) {
                    ColumnKind.MACRO_VALUE -> commitMacroEdit(row, newValue)
                    else -> commitCellEdit(row, column, newValue)
                }
            }
        }

        val cellRenderer = TableViewCellRenderer(tableViewModel, search)
        val table = InsertIndicatorTable(tableModel).apply {
            autoResizeMode = JTable.AUTO_RESIZE_OFF
            background = JBColor.lazy { EditorColorsManager.getInstance().globalScheme.defaultBackground }
            gridColor = TABLE_GRID_COLOR
            setShowGrid(true)
            setCellSelectionEnabled(true)
            rowHeight = JBUI.scale(24)
            tableHeader.reorderingAllowed = false
            setDefaultRenderer(Any::class.java, cellRenderer)
        }

        val rowInsertionSupported = tableViewModel.headerLinePointer != null

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button != MouseEvent.BUTTON1) return

                if (isNavigationClick(e)) {
                    val row = table.rowAtPoint(e.point).takeIf { it >= 0 } ?: return
                    val column = table.columnAtPoint(e.point).takeIf { it >= 0 } ?: return

                    navigateFromCell(fileEditor, tableViewModel, row, table.convertColumnIndexToModel(column), RelativePoint(e), search)
                    return
                }

                if (rowInsertionSupported && table.insertBoundaryRow >= 0) {
                    insertRowAt(tableViewModel, table.insertBoundaryRow)
                    table.insertBoundaryRow = -1
                }
            }

            override fun mousePressed(e: MouseEvent) = maybeShowContextMenu(e)
            override fun mouseReleased(e: MouseEvent) = maybeShowContextMenu(e)

            override fun mouseExited(e: MouseEvent) {
                table.cursor = Cursor.getDefaultCursor()
                table.insertBoundaryRow = -1
            }

            private fun maybeShowContextMenu(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val row = table.rowAtPoint(e.point).takeIf { it >= 0 } ?: return
                val column = table.columnAtPoint(e.point).takeIf { it >= 0 } ?: return

                table.changeSelection(row, column, false, false)
                showRowContextMenu(table, tableViewModel, fileEditor, row, table.convertColumnIndexToModel(column), e)
            }
        })
        table.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                table.insertBoundaryRow = if (rowInsertionSupported) rowInsertBoundary(table, e) else -1

                val row = table.rowAtPoint(e.point)
                val column = table.columnAtPoint(e.point)
                val navigatable = row >= 0 && column >= 0
                    && isNavigationHover(e)
                    && isNavigatableCell(tableViewModel, row, table.convertColumnIndexToModel(column))

                table.cursor = when {
                    navigatable -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    table.insertBoundaryRow >= 0 -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    else -> Cursor.getDefaultCursor()
                }
            }
        })

        table.tableHeader.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1 && !isNavigationHover(e) && table.insertBoundaryColumn >= 0) {
                    val boundary = table.insertBoundaryColumn
                    table.insertBoundaryColumn = -1

                    boundaryParameterIndex(tableViewModel, boundary)?.let { parameterIndex ->
                        insertColumnInline(table, tableViewModel, boundary.coerceAtMost(table.columnCount - 1), parameterIndex)
                    }
                    return
                }

                val column = table.columnAtPoint(e.point).takeIf { it >= 0 } ?: return
                val columnIndex = table.convertColumnIndexToModel(column)

                when {
                    isNavigationClick(e) -> navigateFromColumnHeader(fileEditor, tableViewModel, columnIndex, RelativePoint(e), search)
                    e.button == MouseEvent.BUTTON1 && e.clickCount == 2 -> editColumnInline(table, tableViewModel, columnIndex)
                }
            }

            override fun mousePressed(e: MouseEvent) = maybeShowContextMenu(e)
            override fun mouseReleased(e: MouseEvent) = maybeShowContextMenu(e)

            override fun mouseExited(e: MouseEvent) {
                table.tableHeader.cursor = Cursor.getDefaultCursor()
                table.insertBoundaryColumn = -1
            }

            private fun maybeShowContextMenu(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val column = table.columnAtPoint(e.point).takeIf { it >= 0 } ?: return

                showColumnContextMenu(table, tableViewModel, table.convertColumnIndexToModel(column), e)
            }
        })
        table.tableHeader.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                table.insertBoundaryColumn = if (rowInsertionSupported) columnInsertBoundary(table, tableViewModel, e) else -1

                val column = table.columnAtPoint(e.point)
                val navigatable = column >= 0
                    && isNavigationHover(e)
                    && tableViewModel.columns.getOrNull(table.convertColumnIndexToModel(column))?.kind == ColumnKind.PARAMETER

                table.tableHeader.cursor = when {
                    navigatable -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    table.insertBoundaryColumn >= 0 -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    else -> Cursor.getDefaultCursor()
                }
            }
        })

        search.register(table, tableViewModel)

        applyHeaderRenderer(table, tableViewModel)
        applyColumnWidths(table, tableViewModel, cellRenderer)
        applyCellEditors(table, tableViewModel)

        val panel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.customLine(JBColor.border(), 1)

            add(table.tableHeader, BorderLayout.NORTH)
            add(table, BorderLayout.CENTER)
        }

        return panel to table
    }

    private fun rowInsertBoundary(table: JBTable, e: MouseEvent): Int {
        if (table.columnAtPoint(e.point) != 0) return -1
        val row = table.rowAtPoint(e.point).takeIf { it >= 0 } ?: return -1

        val rect = table.getCellRect(row, 0, true)
        val threshold = JBUI.scale(5)

        return when {
            e.point.y - rect.y <= threshold -> row
            rect.y + rect.height - e.point.y <= threshold -> row + 1
            else -> -1
        }
    }

    private fun insertRowAt(tableViewModel: TableViewModel, boundary: Int) {
        val row = tableViewModel.rows.getOrNull(boundary)

        if (row != null) insertRow(tableViewModel, row, above = true)
        else addRow(tableViewModel)
    }

    private fun columnInsertBoundary(table: InsertIndicatorTable, tableViewModel: TableViewModel, e: MouseEvent): Int {
        if (e.y > table.tableHeader.height / 2) return -1
        val column = table.columnAtPoint(e.point).takeIf { it >= 0 } ?: return -1

        val rect = table.tableHeader.getHeaderRect(column)
        val threshold = JBUI.scale(6)
        val boundary = when {
            e.x - rect.x <= threshold -> column
            rect.x + rect.width - e.x <= threshold -> column + 1
            else -> -1
        }

        return boundary.takeIf { it >= 0 && boundaryParameterIndex(tableViewModel, it) != null }
            ?: -1
    }

    private fun boundaryParameterIndex(tableViewModel: TableViewModel, boundary: Int): Int? =
        if (boundary >= tableViewModel.columns.size) tableViewModel.parameterCount
        else tableViewModel.columns.getOrNull(boundary)?.parameterIndex

    private fun applyHeaderRenderer(table: JBTable, tableViewModel: TableViewModel) {
        val defaultHeaderRenderer = table.tableHeader.defaultRenderer

        val headerRenderer = TableCellRenderer { t, value, isSelected, hasFocus, row, column ->
            val columnModel = tableViewModel.columns[t.convertColumnIndexToModel(column)]

            defaultHeaderRenderer.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column).apply {
                font = if (columnModel.unique) font.deriveFont(Font.BOLD) else font.deriveFont(Font.PLAIN)
                foreground = if (columnModel.problem?.error == true) MODE_REMOVE_COLOR else UIUtil.getLabelForeground()
                asSafely<JComponent>()?.toolTipText = columnModel.problem?.message ?: columnModel.tooltip
            }
        }

        table.columnModel.columns.toList().forEach { it.headerRenderer = headerRenderer }
    }

    private fun applyColumnWidths(table: JBTable, tableViewModel: TableViewModel, cellRenderer: TableViewCellRenderer) {
        val headerRenderer = table.tableHeader.defaultRenderer

        table.columnModel.columns.toList().forEachIndexed { columnIndex, column ->
            var width = headerRenderer.getTableCellRendererComponent(table, column.headerValue, false, false, -1, columnIndex)
                .preferredSize.width + JBUI.scale(16)

            (0 until min(tableViewModel.rows.size, MAX_ROWS_FOR_WIDTH_CALCULATION)).forEach { rowIndex ->
                val cellWidth = cellRenderer.getTableCellRendererComponent(table, null, false, false, rowIndex, columnIndex)
                    .preferredSize.width + JBUI.scale(8)
                width = max(width, cellWidth)
            }

            val preferredWidth = min(width, JBUI.scale(MAX_COLUMN_WIDTH))
            column.minWidth = JBUI.scale(32)
            column.preferredWidth = preferredWidth
            column.width = preferredWidth
        }
    }

    private fun applyCellEditors(table: JBTable, tableViewModel: TableViewModel) {
        table.columnModel.columns.toList().forEachIndexed { columnIndex, tableColumn ->
            val column = tableViewModel.columns[columnIndex]

            tableColumn.cellEditor = when {
                !column.editable -> null
                column.variants.isNotEmpty() -> VariantsCellEditor(tableViewModel, column.variants)
                else -> RawTextCellEditor(tableViewModel)
            }
        }
    }

    // Editing

    private fun showRowContextMenu(
        table: JBTable,
        tableViewModel: TableViewModel,
        fileEditor: ImpExSplitEditorEx,
        rowIndex: Int,
        columnIndex: Int,
        e: MouseEvent,
    ) {
        val row = tableViewModel.rows.getOrNull(rowIndex) ?: return
        val group = DefaultActionGroup()

        row.cells.getOrNull(columnIndex)
            ?.problem
            ?.fixes
            ?.forEach { fixModel ->
                group.add(object : DumbAwareAction(fixModel.name, null, AllIcons.Actions.IntentionBulb) {
                    override fun actionPerformed(event: AnActionEvent) = applyProblemFix(fileEditor, fixModel)
                })
            }

        if (row.valueLinePointer != null) {
            if (group.childrenCount > 0) group.addSeparator()

            group.add(object : DumbAwareAction(i18n("hybris.impex.table_view.insert_row_above"), null, AllIcons.General.Add) {
                override fun actionPerformed(event: AnActionEvent) = insertRow(tableViewModel, row, above = true)
            })
            group.add(object : DumbAwareAction(i18n("hybris.impex.table_view.insert_row_below"), null, AllIcons.General.Add) {
                override fun actionPerformed(event: AnActionEvent) = insertRow(tableViewModel, row, above = false)
            })
            group.addSeparator()
            group.add(object : DumbAwareAction(i18n("hybris.impex.table_view.delete_row"), null, AllIcons.General.Remove) {
                override fun actionPerformed(event: AnActionEvent) = deleteRow(row)
            })
        }

        if (group.childrenCount == 0) return

        ActionManager.getInstance()
            .createActionPopupMenu(ActionPlaces.EDITOR_POPUP, group)
            .component
            .show(table, e.x, e.y)
    }

    private fun applyProblemFix(fileEditor: ImpExSplitEditorEx, fixModel: ProblemFixModel) {
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(fileEditor.editor.document) ?: return

        fixModel.apply(project, psiFile)
        fileEditor.refreshTableView()
    }

    private fun insertRow(tableViewModel: TableViewModel, row: RowModel, above: Boolean) {
        val valueLine = row.valueLinePointer?.element ?: return
        val psiFile = valueLine.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
        val emptyRow = ";".repeat(tableViewModel.parameterCount)

        WriteCommandAction.runWriteCommandAction(project, i18n("hybris.impex.table_view.command.add_row"), null, {
            if (above) {
                val lineStart = document.getLineStartOffset(document.getLineNumber(valueLine.textRange.startOffset))
                document.insertString(lineStart, emptyRow + "\n")
            } else {
                val lineEnd = document.getLineEndOffset(document.getLineNumber(valueLine.textRange.endOffset))
                document.insertString(lineEnd, "\n" + emptyRow)
            }
        }, psiFile)
    }

    private fun deleteRow(row: RowModel) {
        val valueLine = row.valueLinePointer?.element ?: return
        val psiFile = valueLine.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return

        WriteCommandAction.runWriteCommandAction(project, i18n("hybris.impex.table_view.command.delete_row"), null, {
            val start = document.getLineStartOffset(document.getLineNumber(valueLine.textRange.startOffset))
            val end = min(document.getLineEndOffset(document.getLineNumber(valueLine.textRange.endOffset)) + 1, document.textLength)

            document.deleteString(start, end)
        }, psiFile)
    }

    private fun showColumnContextMenu(table: JBTable, tableViewModel: TableViewModel, columnIndex: Int, e: MouseEvent) {
        val column = tableViewModel.columns.getOrNull(columnIndex) ?: return
        if (column.kind != ColumnKind.PARAMETER || column.parameterIndex == null) return

        val editAction = object : DumbAwareAction(i18n("hybris.impex.table_view.edit_column"), null, AllIcons.Actions.Edit) {
            override fun actionPerformed(event: AnActionEvent) = editColumnInline(table, tableViewModel, columnIndex)
        }
        val insertLeftAction = object : DumbAwareAction(i18n("hybris.impex.table_view.insert_column_left"), null, HybrisIcons.ImpEx.Actions.INSERT_COLUMN_LEFT) {
            override fun actionPerformed(event: AnActionEvent) = insertColumnInline(table, tableViewModel, columnIndex, column.parameterIndex)
        }
        val insertRightAction = object : DumbAwareAction(i18n("hybris.impex.table_view.insert_column_right"), null, HybrisIcons.ImpEx.Actions.INSERT_COLUMN_RIGHT) {
            override fun actionPerformed(event: AnActionEvent) = insertColumnInline(table, tableViewModel, columnIndex, column.parameterIndex + 1)
        }
        val deleteAction = object : DumbAwareAction(i18n("hybris.impex.table_view.delete_column"), null, HybrisIcons.ImpEx.Actions.REMOVE_COLUMN) {
            override fun actionPerformed(event: AnActionEvent) = deleteColumn(tableViewModel, column.parameterIndex)
        }

        val group = DefaultActionGroup(editAction, insertLeftAction, insertRightAction).apply {
            addSeparator()
            add(deleteAction)
        }

        ActionManager.getInstance()
            .createActionPopupMenu(ActionPlaces.EDITOR_POPUP, group)
            .component
            .show(table.tableHeader, e.x, e.y)
    }

    private fun editColumnInline(table: JBTable, tableViewModel: TableViewModel, columnIndex: Int) {
        val column = tableViewModel.columns.getOrNull(columnIndex) ?: return
        if (column.kind != ColumnKind.PARAMETER || column.parameterIndex == null) return

        val initialText = tableViewModel.headerLinePointer?.element
            ?.getFullHeaderParameter(column.parameterIndex)
            ?.text
            ?.trim()
            ?: return
        val anchor = table.tableHeader.getHeaderRect(table.convertColumnIndexToView(columnIndex))

        showInlineHeaderEditor(table.tableHeader, anchor, initialText, { attributeCompletions(tableViewModel) }) { text ->
            commitHeaderParameterEdit(tableViewModel, column.parameterIndex, text)
        }
    }

    private fun insertColumnInline(table: JBTable, tableViewModel: TableViewModel, columnIndex: Int, parameterIndex: Int) {
        val anchor = table.tableHeader.getHeaderRect(table.convertColumnIndexToView(columnIndex))

        showInlineHeaderEditor(table.tableHeader, anchor, "", { attributeCompletions(tableViewModel) }) { text ->
            insertColumnAt(tableViewModel, parameterIndex, text)
        }
    }

    private fun editTypeInline(anchorComponent: JComponent, tableViewModel: TableViewModel) {
        val initialText = tableViewModel.headerLinePointer?.element
            ?.fullHeaderType
            ?.text
            ?.trim()
            ?: return

        showInlineHeaderEditor(anchorComponent, Rectangle(0, 0, anchorComponent.width, anchorComponent.height), initialText, { typeCompletions() }) { text ->
            commitHeaderTypeEdit(tableViewModel, text)
        }
    }

    private fun attributeCompletions(tableViewModel: TableViewModel): InlineCompletionItems {
        val modifierNames = AttributeModifier.entries.map { it.modifierName }.sorted()

        if (DumbService.isDumb(project)) return InlineCompletionItems(emptyList(), modifierNames)

        val typeName = tableViewModel.headerLinePointer
            ?.element
            ?.fullHeaderType
            ?.headerTypeName
            ?.text
            ?: return InlineCompletionItems(emptyList(), modifierNames)

        val names = TSCompletionService.getInstance(project)
            .getCompletions(typeName, TSMetaType.META_ITEM, TSMetaType.META_ENUM, TSMetaType.META_RELATION)
            .map { it.lookupString }
            .distinct()
            .sorted()

        return InlineCompletionItems(names, modifierNames)
    }

    private fun typeCompletions(): InlineCompletionItems {
        val modifierNames = TypeModifier.entries.map { it.modifierName }.sorted()

        if (DumbService.isDumb(project)) return InlineCompletionItems(emptyList(), modifierNames)

        val names = TSCompletionService.getInstance(project)
            .getCompletions(TSMetaType.META_ITEM, TSMetaType.META_ENUM, TSMetaType.META_RELATION)
            .map { it.lookupString }
            .distinct()
            .sorted()

        return InlineCompletionItems(names, modifierNames)
    }

    private fun showInlineHeaderEditor(
        anchorComponent: JComponent,
        anchor: Rectangle,
        initialText: String,
        completionItems: () -> InlineCompletionItems,
        onCommit: (String) -> Unit,
    ) {
        coroutineScope.launch {
            if (project.isDisposed) return@launch

            val completions = readAction {
                try {
                    completionItems()
                } catch (_: IndexNotReadyException) {
                    InlineCompletionItems(emptyList(), emptyList())
                }
            }

            withContext(Dispatchers.EDT) {
                val field = TextFieldWithAutoCompletion(
                    project,
                    InlineHeaderCompletionProvider(completions),
                    false,
                    initialText,
                )
                WrappingExpandableEditorSupport(field)

                val fontMetrics = anchorComponent.getFontMetrics(JBFont.label())
                val minWidth = max(anchor.width + JBUI.scale(32), JBUI.scale(280)).coerceAtMost(JBUI.scale(720))
                val width = (fontMetrics.stringWidth(initialText) + JBUI.scale(80)).coerceIn(minWidth, JBUI.scale(720))
                field.preferredSize = Dimension(width, JBUI.scale(30))

                val popup = JBPopupFactory.getInstance()
                    .createComponentPopupBuilder(field, field)
                    .setRequestFocus(true)
                    .setResizable(true)
                    .createPopup()

                val commitAction = object : DumbAwareAction() {
                    override fun getActionUpdateThread() = ActionUpdateThread.EDT

                    override fun update(e: AnActionEvent) {
                        e.presentation.isEnabled = field.editor?.let { LookupManager.getActiveLookup(it) } == null
                    }

                    override fun actionPerformed(e: AnActionEvent) {
                        popup.closeOk(null)

                        field.text.trim()
                            .takeIf { it.isNotEmpty() && it != initialText.trim() }
                            ?.let { onCommit(it) }
                    }
                }
                commitAction.registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)), field)

                popup.show(RelativePoint(anchorComponent, Point(anchor.x, anchor.y)))
            }
        }
    }

    private fun commitHeaderParameterEdit(tableViewModel: TableViewModel, parameterIndex: Int, newText: String) {
        val headerLine = tableViewModel.headerLinePointer?.element ?: return
        val parameter = headerLine.getFullHeaderParameter(parameterIndex) ?: return
        val psiFile = headerLine.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return

        WriteCommandAction.runWriteCommandAction(project, i18n("hybris.impex.table_view.command.update_header"), null, {
            document.replaceString(parameter.textRange.startOffset, parameter.textRange.endOffset, newText)
        }, psiFile)
    }

    private fun commitHeaderTypeEdit(tableViewModel: TableViewModel, newText: String) {
        val fullHeaderType = tableViewModel.headerLinePointer?.element?.fullHeaderType ?: return
        val psiFile = fullHeaderType.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return

        WriteCommandAction.runWriteCommandAction(project, i18n("hybris.impex.table_view.command.update_header"), null, {
            document.replaceString(fullHeaderType.textRange.startOffset, fullHeaderType.textRange.endOffset, newText)
        }, psiFile)
    }

    private fun insertColumnAt(tableViewModel: TableViewModel, parameterIndex: Int, name: String) {
        val headerLine = tableViewModel.headerLinePointer?.element ?: return
        val psiFile = headerLine.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
        val parameters = headerLine.fullHeaderParameterList

        WriteCommandAction.runWriteCommandAction(project, i18n("hybris.impex.table_view.command.add_column"), null, {
            val insertions = buildList {
                val anchorParameter = parameters.getOrNull(parameterIndex)

                if (anchorParameter != null) {
                    add(anchorParameter.textRange.startOffset to "$name; ")
                    headerLine.valueLines.forEach { line ->
                        line.getValueGroup(parameterIndex)?.let { add(it.textRange.startOffset to ";") }
                    }
                } else {
                    add(headerLine.textRange.endOffset to "; $name")
                    headerLine.valueLines.forEach { add(it.textRange.endOffset to ";") }
                }
            }.sortedByDescending { it.first }

            insertions.forEach { (offset, insertion) -> document.insertString(offset, insertion) }
        }, psiFile)
    }

    private fun deleteColumn(tableViewModel: TableViewModel, parameterIndex: Int) {
        val headerLine = tableViewModel.headerLinePointer?.element ?: return
        val parameter = headerLine.getFullHeaderParameter(parameterIndex) ?: return
        val psiFile = headerLine.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return

        WriteCommandAction.runWriteCommandAction(project, i18n("hybris.impex.table_view.command.delete_column"), null, {
            val deletions = buildList {
                val text = document.charsSequence
                var separatorIndex = parameter.textRange.startOffset - 1
                while (separatorIndex >= 0 && text[separatorIndex] != ';') separatorIndex--

                add((if (separatorIndex >= 0) separatorIndex else parameter.textRange.startOffset) to parameter.textRange.endOffset)
                headerLine.valueLines.forEach { line ->
                    line.getValueGroup(parameterIndex)?.let { add(it.textRange.startOffset to it.textRange.endOffset) }
                }
            }.sortedByDescending { it.first }

            deletions.forEach { (start, end) -> document.deleteString(start, end) }
        }, psiFile)
    }

    private fun commitCellEdit(row: RowModel, column: ColumnModel, newValue: String) {
        val text = newValue.trim()
        val valueLine = row.valueLinePointer?.element ?: return
        val psiFile = valueLine.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return

        WriteCommandAction.runWriteCommandAction(project, i18n("hybris.impex.table_view.command.update_value"), null, {
            when {
                column.kind == ColumnKind.SUB_TYPE -> {
                    val subTypeName = valueLine.subTypeName

                    if (subTypeName != null) document.replaceString(subTypeName.textRange.startOffset, subTypeName.textRange.endOffset, text)
                    else if (text.isNotEmpty()) document.insertString(valueLine.textRange.startOffset, "$text ")
                }

                column.parameterIndex != null -> {
                    val valueGroup = valueLine.getValueGroup(column.parameterIndex)

                    if (valueGroup != null) {
                        val value = valueGroup.value

                        if (value != null) document.replaceString(value.textRange.startOffset, value.textRange.endOffset, text)
                        else if (text.isNotEmpty()) document.insertString(valueGroup.textRange.endOffset, " $text")
                    } else {
                        val missingGroups = column.parameterIndex + 1 - valueLine.valueGroupList.size
                        val suffix = if (text.isNotEmpty()) " $text" else ""

                        document.insertString(valueLine.textRange.endOffset, ";".repeat(missingGroups) + suffix)
                    }
                }
            }
        }, psiFile)
    }

    private fun commitMacroEdit(row: RowModel, newValue: String) {
        val text = newValue.trim()
        val declaration = row.macroDeclarationPointer?.element ?: return
        val psiFile = declaration.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return

        WriteCommandAction.runWriteCommandAction(project, i18n("hybris.impex.table_view.command.update_macro"), null, {
            document.replaceString(declaration.macroNameDec.textRange.endOffset, declaration.textRange.endOffset, " = $text")
        }, psiFile)
    }

    private fun addRow(tableViewModel: TableViewModel) {
        val headerLine = tableViewModel.headerLinePointer?.element ?: return
        val psiFile = headerLine.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
        val anchor = headerLine.valueLines.lastOrNull() ?: headerLine

        WriteCommandAction.runWriteCommandAction(project, i18n("hybris.impex.table_view.command.add_row"), null, {
            document.insertString(anchor.textRange.endOffset, "\n" + ";".repeat(tableViewModel.parameterCount))
        }, psiFile)
    }

    private class InsertIndicatorTable(model: AbstractTableModel) : JBTable(model) {

        var insertBoundaryRow: Int = -1
            set(value) {
                if (field != value) {
                    field = value
                    repaint()
                }
            }

        var insertBoundaryColumn: Int = -1
            set(value) {
                if (field != value) {
                    field = value
                    repaint()
                    tableHeader?.repaint()
                }
            }

        override fun changeSelection(rowIndex: Int, columnIndex: Int, toggle: Boolean, extend: Boolean) =
            super.changeSelection(rowIndex, columnIndex, false, extend)

        override fun createDefaultTableHeader(): JTableHeader = object : JBTableHeader() {
            override fun paint(g: Graphics) {
                super.paint(g)

                val boundary = insertBoundaryColumn.takeIf { it >= 0 } ?: return
                val x = boundaryX(boundary)
                val icon = AllIcons.General.InlineAdd

                g.color = INSERT_INDICATOR_COLOR
                g.fillRect(max(0, x - JBUI.scale(1)), 0, JBUI.scale(2), height)
                icon.paintIcon(this, g, max(0, x - icon.iconWidth / 2), 0)
            }
        }

        override fun paint(g: Graphics) {
            super.paint(g)

            paintRowBoundary(g)
            paintColumnBoundary(g)
        }

        private fun paintRowBoundary(g: Graphics) {
            val boundary = insertBoundaryRow.takeIf { it >= 0 } ?: return
            if (rowCount == 0) return

            val y = if (boundary < rowCount) getCellRect(boundary, 0, true).y
            else getCellRect(rowCount - 1, 0, true).let { it.y + it.height }
            val icon = AllIcons.General.InlineAdd

            g.color = INSERT_INDICATOR_COLOR
            g.fillRect(0, max(0, y - JBUI.scale(1)), width, JBUI.scale(2))
            icon.paintIcon(this, g, JBUI.scale(2), max(0, y - icon.iconHeight / 2))
        }

        private fun paintColumnBoundary(g: Graphics) {
            val boundary = insertBoundaryColumn.takeIf { it >= 0 } ?: return

            g.color = INSERT_INDICATOR_COLOR
            g.fillRect(max(0, boundaryX(boundary) - JBUI.scale(1)), 0, JBUI.scale(2), height)
        }

        fun boundaryX(boundary: Int): Int {
            var x = 0
            (0 until boundary.coerceAtMost(columnCount)).forEach { x += columnModel.getColumn(it).width }
            return x
        }
    }

    private class TableViewSearch {

        private val tables = mutableListOf<Pair<JBTable, TableViewModel>>()
        private val matches = mutableListOf<SearchMatch>()
        private var matchIndex = -1

        var query: String = ""
            private set

        fun register(table: JBTable, tableViewModel: TableViewModel) {
            tables.add(table to tableViewModel)
        }

        fun update(newQuery: String) {
            query = newQuery.trim()
            matches.clear()
            matchIndex = -1

            if (query.isNotEmpty()) {
                tables.forEach { (table, tableViewModel) ->
                    tableViewModel.rows.forEachIndexed { rowIndex, row ->
                        row.cells.forEachIndexed { columnIndex, cell ->
                            if (cell.matches(query)) matches.add(SearchMatch(table, tableViewModel, rowIndex, columnIndex))
                        }
                    }
                }
            }

            tables.forEach { (table, _) -> table.repaint() }

            if (matches.isNotEmpty()) goTo(0)
        }

        fun next() {
            if (matches.isEmpty()) return
            goTo((matchIndex + 1) % matches.size)
        }

        fun previous() {
            if (matches.isEmpty()) return
            goTo((matchIndex - 1 + matches.size) % matches.size)
        }

        fun currentMatch(): SearchMatch? = matches.getOrNull(matchIndex.coerceAtLeast(0))

        fun allMatches(): List<SearchMatch> = matches.toList()

        fun isMatch(cell: CellModel) = query.isNotEmpty() && cell.matches(query)

        fun selectElement(target: PsiElement): Boolean = selectMacroDeclaration(target)
            || selectValueLine(target)
            || selectHeaderLine(target)

        private fun selectMacroDeclaration(target: PsiElement): Boolean {
            val declaration = target.parentOfType<ImpExMacroDeclaration>(true) ?: return false

            tables.forEach { (table, tableViewModel) ->
                val keyColumnIndex = tableViewModel.columns.indexOfFirst { it.kind == ColumnKind.MACRO_KEY }
                    .takeIf { it >= 0 }
                    ?: return@forEach

                tableViewModel.rows.forEachIndexed { rowIndex, row ->
                    if (row.macroDeclarationPointer?.element?.isEquivalentTo(declaration) == true) {
                        select(table, rowIndex, keyColumnIndex)
                        return true
                    }
                }
            }

            return false
        }

        private fun selectValueLine(target: PsiElement): Boolean {
            val valueLine = target.parentOfType<ImpExValueLine>(true) ?: return false
            val valueGroup = target.parentOfType<ImpExValueGroup>(true)

            tables.forEach { (table, tableViewModel) ->
                tableViewModel.rows.forEachIndexed { rowIndex, row ->
                    if (row.valueLinePointer?.element?.isEquivalentTo(valueLine) == true) {
                        val columnIndex = valueGroup
                            ?.let { group -> tableViewModel.columns.indexOfFirst { it.parameterIndex == group.columnNumber } }
                            ?.takeIf { it >= 0 }
                            ?: 0

                        select(table, rowIndex, columnIndex)
                        return true
                    }
                }
            }

            return false
        }

        private fun selectHeaderLine(target: PsiElement): Boolean {
            val headerLine = target.parentOfType<ImpExHeaderLine>(true) ?: return false

            tables.forEach { (table, tableViewModel) ->
                if (tableViewModel.headerLinePointer?.element?.isEquivalentTo(headerLine) == true) {
                    if (tableViewModel.rows.isNotEmpty()) select(table, 0, 0)
                    else table.scrollRectToVisible(Rectangle(0, 0, 1, table.height))

                    return true
                }
            }

            return false
        }

        private fun select(table: JBTable, rowIndex: Int, columnIndex: Int) {
            table.changeSelection(rowIndex, columnIndex, false, false)
            table.scrollRectToVisible(table.getCellRect(rowIndex, columnIndex, true))
            table.requestFocusInWindow()
        }

        private fun goTo(index: Int) {
            matchIndex = index
            val match = matches[index]

            match.table.changeSelection(match.rowIndex, match.columnIndex, false, false)
            match.table.scrollRectToVisible(match.table.getCellRect(match.rowIndex, match.columnIndex, true))
        }

        private fun CellModel.matches(query: String) = text.contains(query, true) || rawText.contains(query, true)
    }

    private data class SearchMatch(
        val table: JBTable,
        val tableViewModel: TableViewModel,
        val rowIndex: Int,
        val columnIndex: Int,
    )

    private class TableViewCellRenderer(
        private val tableViewModel: TableViewModel,
        private val search: TableViewSearch,
    ) : DefaultTableCellRenderer() {

        override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
            val cell = tableViewModel.rows.getOrNull(row)
                ?.cells
                ?.getOrNull(table.convertColumnIndexToModel(column))
            val displayValue = cell?.let { tokenizedHtml(it) ?: it.text } ?: value

            super.getTableCellRendererComponent(table, displayValue, isSelected, hasFocus, row, column)

            border = JBUI.Borders.empty(2, 8)
            toolTipText = cell?.problem?.message
                ?: cell?.tooltip
                ?: cell?.text?.takeIf { it.length > 60 }
            font = if (cell?.substituted == true || cell?.valueKind == ValueKind.REFERENCE) JBFont.label().asItalic() else JBFont.label()
            horizontalAlignment = if (cell?.alignRight == true) RIGHT else LEFT

            if (!isSelected) {
                background = when {
                    cell != null && search.isMatch(cell) -> SEARCH_MATCH_BACKGROUND
                    cell?.problem?.error == true -> ERROR_CELL_BACKGROUND
                    cell?.problem != null -> WARNING_CELL_BACKGROUND
                    else -> table.background
                }
                foreground = when {
                    cell?.muted == true -> NamedColorUtil.getInactiveTextColor()
                    cell?.substituted == true -> SUBSTITUTED_VALUE_COLOR
                    cell?.valueKind == ValueKind.STRING -> STRING_VALUE_COLOR
                    cell?.valueKind == ValueKind.NUMBER -> NUMBER_VALUE_COLOR
                    else -> table.foreground
                }
            }

            return this
        }

        private fun tokenizedHtml(cell: CellModel): String? {
            if (cell.valueKind == ValueKind.STRING || cell.muted) return null

            val tokens = cell.text.split(',')
            if (tokens.size < 2) return null

            val numberColor = ColorUtil.toHex(NUMBER_VALUE_COLOR)
            val stringColor = ColorUtil.toHex(STRING_VALUE_COLOR)
            val separatorColor = ColorUtil.toHex(NamedColorUtil.getInactiveTextColor())

            return tokens.joinToString("<font color='#$separatorColor'>, </font>", "<html>", "</html>") { token ->
                val trimmed = StringUtil.escapeXmlEntities(token.trim())

                when {
                    trimmed.isNotEmpty() && trimmed.toDoubleOrNull() != null -> "<font color='#$numberColor'>$trimmed</font>"
                    trimmed.length >= 2 && (trimmed.first() == '"' || trimmed.first() == '\'') && trimmed.last() == trimmed.first() ->
                        "<font color='#$stringColor'>$trimmed</font>"

                    else -> trimmed
                }
            }
        }
    }

    private class RawTextCellEditor(private val tableViewModel: TableViewModel) : DefaultCellEditor(
        ExpandableTextField(
            { text -> text.split(LINE_CONTINUATION_REGEX) },
            { lines -> lines.joinToString(" \\\n") },
        )
    ) {

        init {
            clickCountToStart = 2
        }

        override fun getTableCellEditorComponent(table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
            val rawText = tableViewModel.rows.getOrNull(row)
                ?.cells
                ?.getOrNull(table.convertColumnIndexToModel(column))
                ?.rawText

            return super.getTableCellEditorComponent(table, rawText ?: value, isSelected, row, column)
        }
    }

    private class VariantsCellEditor(private val tableViewModel: TableViewModel, variants: List<String>) : DefaultCellEditor(
        ComboBox(variants.toTypedArray()).apply { isEditable = true }
    ) {

        init {
            clickCountToStart = 2
        }

        override fun getTableCellEditorComponent(table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
            val rawText = tableViewModel.rows.getOrNull(row)
                ?.cells
                ?.getOrNull(table.convertColumnIndexToModel(column))
                ?.rawText

            return super.getTableCellEditorComponent(table, rawText ?: value, isSelected, row, column)
        }
    }

    private enum class ColumnKind {
        LINE_NUMBER, SUB_TYPE, PARAMETER, MACRO_KEY, MACRO_VALUE,
    }

    private enum class ValueKind {
        DEFAULT, STRING, NUMBER, REFERENCE,
    }

    private data class ColumnTypeMeta(
        val variants: List<String> = emptyList(),
        val reference: Boolean = false,
    )

    private data class NavigationTarget(
        val label: String,
        val pointer: SmartPsiElementPointer<PsiElement>,
    )

    private data class InlineCompletionItems(
        val names: List<String>,
        val modifiers: List<String>,
    )

    private class WrappingExpandableEditorSupport(field: EditorTextField) : ExpandableEditorSupport(field) {

        override fun initPopupEditor(editor: EditorEx, background: Color?) {
            super.initPopupEditor(editor, background)

            editor.settings.isUseSoftWraps = true
        }
    }

    private class InlineHeaderCompletionProvider(
        private val items: InlineCompletionItems,
    ) : TextFieldWithAutoCompletion.StringsCompletionProvider(items.names + items.modifiers, null) {

        override fun getPrefix(text: String, offset: Int): String {
            val start = text.lastIndexOfAny(PREFIX_SEPARATORS, (offset - 1).coerceAtLeast(0)) + 1

            return text.substring(start.coerceAtMost(offset), offset)
        }

        override fun getItems(prefix: String?, cached: Boolean, parameters: CompletionParameters?): Collection<String> {
            val textBeforeCaret = parameters
                ?.editor
                ?.document
                ?.charsSequence
                ?.subSequence(0, parameters.offset)
                ?.toString()
            val insideModifiers = textBeforeCaret
                ?.let { it.lastIndexOf('[') > it.lastIndexOf(']') }
                ?: false

            return if (insideModifiers) items.modifiers else items.names
        }

        companion object {
            private val PREFIX_SEPARATORS = charArrayOf(' ', '\n', '\t', '[', ']', '(', ')', ',', '=', ';')
        }
    }

    private data class CellProblem(
        val message: String,
        val error: Boolean,
        val fixes: List<ProblemFixModel> = emptyList(),
    )

    private class ProblemFixModel(
        val name: String,
        private val descriptor: ProblemDescriptor,
        private val fix: QuickFix<*>,
    ) {

        fun apply(project: Project, psiFile: PsiFile) {
            if (descriptor.psiElement?.isValid != true) return

            WriteCommandAction.runWriteCommandAction(project, name, null, {
                @Suppress("UNCHECKED_CAST")
                (fix as QuickFix<ProblemDescriptor>).applyFix(project, descriptor)
            }, psiFile)
        }
    }

    private data class TableViewModel(
        val title: String,
        val typeName: String?,
        val typeModifiers: String?,
        val titleProblem: CellProblem?,
        val headerLinePointer: SmartPsiElementPointer<ImpExHeaderLine>?,
        val parameterCount: Int,
        val columns: List<ColumnModel>,
        val rows: List<RowModel>,
    )

    private data class ColumnModel(
        val name: String,
        val tooltip: String?,
        val kind: ColumnKind,
        val unique: Boolean = false,
        val editable: Boolean = false,
        val parameterIndex: Int? = null,
        val variants: List<String> = emptyList(),
        val reference: Boolean = false,
        val problem: CellProblem? = null,
    )

    private data class RowModel(
        val cells: MutableList<CellModel>,
        val valueLinePointer: SmartPsiElementPointer<ImpExValueLine>? = null,
        val macroDeclarationPointer: SmartPsiElementPointer<ImpExMacroDeclaration>? = null,
    )

    private data class CellModel(
        val text: String,
        val rawText: String = "",
        val tooltip: String? = null,
        val muted: Boolean = false,
        val substituted: Boolean = false,
        val alignRight: Boolean = false,
        val valueKind: ValueKind = ValueKind.DEFAULT,
        val problem: CellProblem? = null,
    )

    companion object {
        private const val COLUMN_LINE_NUMBER = "#"
        private const val MAX_ROWS_FOR_WIDTH_CALCULATION = 200
        private const val MAX_COLUMN_WIDTH = 500

        private val KEY_TABLE_VIEW_SEARCH_QUERY = Key.create<String>("impex.table_view.search_query")
        private val KEY_TABLE_VIEW_REPLACE_TEXT = Key.create<String>("impex.table_view.replace_text")
        private val KEY_TABLE_VIEW_REPLACE_VISIBLE = Key.create<Boolean>("impex.table_view.replace_visible")

        private val BOOLEAN_TYPES = setOf("java.lang.Boolean", "boolean")
        private val LINE_CONTINUATION_REGEX = Regex("\\\\\\s*\\n\\s*")

        private val TABLE_GRID_COLOR = JBColor(0xEBECF0, 0x393B40)
        private val SUBSTITUTED_VALUE_COLOR = JBColor(0x067D17, 0x6AAB73)
        private val STRING_VALUE_COLOR = JBColor(0x067D17, 0x6AAB73)
        private val NUMBER_VALUE_COLOR = JBColor(0x1750EB, 0x2AACB8)
        private val INSERT_INDICATOR_COLOR = JBColor(0x217346, 0x549159)
        private val SEARCH_MATCH_BACKGROUND = JBColor(0xFFE9A8, 0x665B34)
        private val ERROR_CELL_BACKGROUND = JBColor(0xFFDCDC, 0x5A3A3A)
        private val WARNING_CELL_BACKGROUND = JBColor(0xFFF6DE, 0x4E4A33)
        private val MODE_INSERT_COLOR = JBColor(0x067D17, 0x549159)
        private val MODE_INSERT_UPDATE_COLOR = JBColor(0x00627A, 0x2AACB8)
        private val MODE_UPDATE_COLOR = JBColor(0x1750EB, 0x548AF7)
        private val MODE_REMOVE_COLOR = JBColor(0xC7222D, 0xF75464)

        fun getInstance(project: Project): ImpExInEditorTableView = project.service()
    }
}
