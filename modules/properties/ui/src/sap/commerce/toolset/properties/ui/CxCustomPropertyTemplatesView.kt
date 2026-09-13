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

package sap.commerce.toolset.properties.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sap.commerce.toolset.properties.custom.CxCustomPropertyTemplateService
import sap.commerce.toolset.properties.meta.CxPropertyCollector
import sap.commerce.toolset.properties.presentation.CxPropertyPresentation
import sap.commerce.toolset.ui.addDocumentListener
import sap.commerce.toolset.ui.event.documentListener
import javax.swing.JComponent
import javax.swing.JLabel

class CxCustomPropertyTemplatesView(private val project: Project) : Disposable {
    private var templateUUID: String = ""
    private val showDataPanel = AtomicBooleanProperty(false)
    private val canApply = AtomicBooleanProperty(false)

    private lateinit var keyFilterField: JBTextField
    private lateinit var valueFilterField: JBTextField
    private lateinit var addKeyField: JBTextField
    private lateinit var addValueField: JBTextField
    private lateinit var statusLabel: JLabel

    private val job = SupervisorJob()
    private val viewScope = CoroutineScope(Dispatchers.Default + job)

    private var properties: List<CxPropertyPresentation> = emptyList()

    private val listModel = CollectionListModel<CxPropertyPresentation>()

    /**
     * A template built from a remote instance holds every property that instance has, so the rows have to be
     * virtualized: the list only ever builds renderer components for what is on screen.
     */
    private val propertyList = CxPropertyList(
        parentDisposable = this,
        model = listModel,
        onReportClicked = { showReport(it) },
        onEditClicked = { startInlineEdit(it) },
        onDeleteClicked = { confirmAndDelete(it) },
    )

    private val lazyViewPanel by lazy {
        object : ClearableLazyValue<DialogPanel>() {
            override fun compute(): DialogPanel {
                lateinit var dPanel: DialogPanel
                return panel {
                    row {
                        keyFilterField = textField()
                            .align(AlignX.FILL)
                            .resizableColumn()
                            .applyToComponent {
                                emptyText.text = "Filter by key"
                                document.addDocumentListener(this@CxCustomPropertyTemplatesView, documentListener { refreshDataView() })
                            }
                            .component

                        valueFilterField = textField()
                            .align(AlignX.FILL)
                            .resizableColumn()
                            .applyToComponent {
                                emptyText.text = "Filter by value"
                                document.addDocumentListener(this@CxCustomPropertyTemplatesView, documentListener { refreshDataView() })
                            }
                            .component
                    }.visibleIf(showDataPanel).layout(RowLayout.PARENT_GRID)

                    row {
                        addKeyField = textField()
                            .align(AlignX.FILL)
                            .resizableColumn()
                            .validationOnInput { validatePropertyKey(it.text) }
                            .validationOnApply { validatePropertyKey(it.text) }
                            .applyToComponent {
                                emptyText.text = "Key"
                            }
                            .component

                        addValueField = textField()
                            .align(AlignX.FILL)
                            .resizableColumn()
                            .applyToComponent {
                                emptyText.text = "Value"
                            }
                            .component

                        button("Apply Property") {
                            canApply.set(dPanel.validateAll().all { it.okEnabled })
                            if (!canApply.get()) return@button

                            CxCustomPropertyTemplateService.getInstance(project)
                                .addProperty(templateUUID, addKeyField.text.trim(), addValueField.text)
                            addKeyField.text = ""
                            addValueField.text = ""
                        }
                    }.visibleIf(showDataPanel).layout(RowLayout.PARENT_GRID)

                    separator(JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR)
                        .visibleIf(showDataPanel)

                    row {
                        cell(
                            JBScrollPane(propertyList).apply {
                                border = null
                                background = propertyList.background
                                viewport.background = propertyList.background
                                setColumnHeaderView(propertyColumnHeader(propertyList.background, CxPropertyRowAction.totalHitWidth))
                            }
                        ).align(Align.FILL).visibleIf(showDataPanel)
                    }.resizableRow()

                    row {
                        statusLabel = label("").component
                        cell(statusLabel).align(AlignX.FILL)
                    }.visibleIf(showDataPanel)
                }.apply {
                    border = JBUI.Borders.empty(JBUI.insets(10, 16, 0, 16))
                    dPanel = this
                }
            }
        }
    }

    override fun dispose() {
        job.cancel()
        propertyList.cancelEdit()
        lazyViewPanel.drop()
    }

    suspend fun render(coroutineScope: CoroutineScope, templateUUID: String, properties: Collection<CxPropertyPresentation>): JComponent {
        this.templateUUID = templateUUID
        this.properties = properties.sortedBy { it.key }
        val viewPanel = lazyViewPanel.value

        toggleView(showDataPanel)
        withContext(Dispatchers.EDT) {
            propertyList.cancelEdit()
            renderData()
        }

        coroutineScope.launch { compareWithProject(this@CxCustomPropertyTemplatesView.properties) }

        return viewPanel
    }

    /** Holds the template against what the project's own files resolve to, marking the rows which disagree. */
    private suspend fun compareWithProject(properties: List<CxPropertyPresentation>) {
        val chain = smartReadAction(project) { CxPropertyCollector.getInstance(project).collect() }
        val counterpart = CxPropertyCounterpart(
            ownLabel = "Template",
            otherLabel = "Project",
            values = chain.resolveProperties(properties.map { it.key }).mapValues { (_, resolved) -> resolved.value },
        )

        withContext(Dispatchers.EDT) { propertyList.counterpart = counterpart }
    }

    private fun refreshDataView() {
        if (!::keyFilterField.isInitialized) return
        renderData()
    }

    private fun renderData() {
        val keyNeedle = keyFilterField.text.trim()
        val valueNeedle = valueFilterField.text.trim()
        val filtered = properties.filter { property ->
            (keyNeedle.isBlank() || property.key.contains(keyNeedle, ignoreCase = true)) &&
                (valueNeedle.isBlank() || property.value.contains(valueNeedle, ignoreCase = true))
        }

        listModel.replaceAll(filtered)
        propertyList.emptyText.text = if (properties.isEmpty()) "Please, use the panel above to add a property."
        else "No properties match the current filter."
        statusLabel.text = when {
            filtered.size == properties.size -> "${properties.size} propert${if (properties.size == 1) "y" else "ies"}"
            else -> "${filtered.size} of ${properties.size} properties"
        }
    }

    private fun startInlineEdit(property: CxPropertyPresentation) {
        propertyList.beginEdit(property) { newValue ->
            if (newValue == property.value) return@beginEdit

            CxCustomPropertyTemplateService.getInstance(project)
                .updateProperty(templateUUID, property.key, newValue)
        }
    }

    private fun confirmAndDelete(property: CxPropertyPresentation) {
        val confirmed = Messages.showYesNoDialog(
            project,
            "Delete property '${property.key}' from this template?",
            "Delete Property",
            Messages.getQuestionIcon(),
        ) == Messages.YES
        if (!confirmed) return

        propertyList.cancelEdit()
        CxCustomPropertyTemplateService.getInstance(project).deleteProperty(templateUUID, property.key)
    }

    private fun showReport(property: CxPropertyPresentation) {
        viewScope.launch {
            val chain = smartReadAction(project) { CxPropertyCollector.getInstance(project).collect() }

            withContext(Dispatchers.EDT) {
                CxPropertyReportDialog(project, property, chain[property.key], chain.resolve(property.key)).show()
            }
        }
    }

    private fun validatePropertyKey(value: String): ValidationInfo? = when {
        value.isBlank() -> ValidationInfo("Property key is not allowed to be empty")
        value.any(Char::isWhitespace) -> ValidationInfo("Property key cannot contain whitespace")
        else -> null
    }

    private fun toggleView(vararg unhide: AtomicBooleanProperty) = listOf(showDataPanel)
        .forEach { it.set(unhide.contains(it)) }

}
