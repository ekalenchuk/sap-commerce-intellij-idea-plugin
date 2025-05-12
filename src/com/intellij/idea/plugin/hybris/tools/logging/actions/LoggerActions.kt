/*
 * This file is part of "SAP Commerce Developers Toolset" plugin for IntelliJ IDEA.
 * Copyright (C) 2019-2024 EPAM Systems <hybrisideaplugin@epam.com> and contributors
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

package com.intellij.idea.plugin.hybris.tools.logging.actions

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.idea.plugin.hybris.common.HybrisConstants
import com.intellij.idea.plugin.hybris.common.utils.HybrisIcons
import com.intellij.idea.plugin.hybris.notifications.Notifications
import com.intellij.idea.plugin.hybris.tools.logging.CxLoggerAccess
import com.intellij.idea.plugin.hybris.tools.remote.RemoteConnectionType
import com.intellij.idea.plugin.hybris.tools.remote.RemoteConnectionUtil
import com.intellij.idea.plugin.hybris.tools.remote.http.AbstractHybrisHacHttpClient
import com.intellij.idea.plugin.hybris.tools.remote.http.HybrisHacHttpClient
import com.intellij.idea.plugin.hybris.util.PackageUtils
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import javax.swing.Icon


abstract class AbstractLoggerAction(private val logLevel: String, val icon: Icon) : AnAction(logLevel, "", icon) {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val dataContext = e.dataContext
        val logIdentifier = dataContext.getData(HybrisConstants.LOGGER_IDENTIFIER_DATA_CONTEXT_KEY)

        if (logIdentifier == null) {
            notify(
                project,
                NotificationType.ERROR,
                "Unable to change the log level",
                "Cannot retrieve a logger name."
            )
            return
        }

        ApplicationManager.getApplication().runReadAction {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Execute HTTP Call to SAP Commerce...") {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        val result = HybrisHacHttpClient.getInstance(project).executeLogUpdate(
                            project,
                            logIdentifier,
                            logLevel,
                            AbstractHybrisHacHttpClient.DEFAULT_HAC_TIMEOUT
                        )

                        val server = RemoteConnectionUtil.getActiveRemoteConnectionSettings(project, RemoteConnectionType.Hybris)
                        val abbreviationLogIdentifier = PackageUtils.abbreviatePackageName(logIdentifier)

                        if (result.statusCode == 200) {
                            notify(
                                project,
                                NotificationType.INFORMATION,
                                "Log level updated",
                                """
                                    <p>Level  : $logLevel</p>
                                    <p>Logger : $abbreviationLogIdentifier</p>
                                    <p>${server.shortenConnectionName()}</p>"""
                            )
                            CxLoggerAccess.getInstance(project).refresh()


                            val editor = e.dataContext.getData<Editor>(CommonDataKeys.EDITOR) ?: return
                            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return

                            forceInlayRefreshViaEdit(project, psiFile)

                        } else {
                            notify(
                                project,
                                NotificationType.ERROR,
                                "Failed to update log level",
                                """
                                    <p>Level  : $logLevel</p>
                                    <p>Logger : $abbreviationLogIdentifier</p>
                                    <p>${server.shortenConnectionName()}</p>"""
                            )
                        }
                    } finally {
                    }
                }
            })
        }
    }

    fun forceInlayRefreshViaEdit(project: Project, psiFile: PsiFile) {
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return

        if (document.isWritable) {
            ApplicationManager.getApplication().invokeLater {
                WriteCommandAction.runWriteCommandAction(project) {
                    val lastLine = document.lineCount - 1
                    val insertOffset = document.getLineEndOffset(lastLine)

                    // Insert and delete a dummy newline to trigger editor updates
                    document.insertString(insertOffset, "\n")
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    document.deleteString(insertOffset, insertOffset + 1)
                    PsiDocumentManager.getInstance(project).commitDocument(document)

                }
            }
        } else {
//            ApplicationManager.getApplication().invokeLater {
//                ReadAction.run {
//                    val documentImpl = document as? DocumentImpl ?: return@run
//                    documentImpl.modificationStamp += 1
//                    PsiDocumentManager.getInstance(project).commitDocument(documentImpl)
//                    //DaemonCodeAnalyzerEx.getInstanceEx(project).restart(psiFile)
//                }
//            }

        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val isRightPlace = "GoToAction" != e.place
        e.presentation.isEnabled = isRightPlace
        e.presentation.isVisible = isRightPlace
    }

    private fun notify(
        project: Project,
        notificationType: NotificationType,
        title: String,
        content: String
    ) {
        Notifications.create(
            notificationType,
            title,
            content
        )
            .hideAfter(5)
            .notify(project)
    }

}

class AllLoggerAction : AbstractLoggerAction("ALL", HybrisIcons.Log.Level.ALL)
class OffLoggerAction : AbstractLoggerAction("OFF", HybrisIcons.Log.Level.OFF)
class TraceLoggerAction : AbstractLoggerAction("TRACE", HybrisIcons.Log.Level.TRACE)
class DebugLoggerAction : AbstractLoggerAction("DEBUG", HybrisIcons.Log.Level.DEBUG)
class InfoLoggerAction : AbstractLoggerAction("INFO", HybrisIcons.Log.Level.INFO)
class WarnLoggerAction : AbstractLoggerAction("WARN", HybrisIcons.Log.Level.WARN)
class ErrorLoggerAction : AbstractLoggerAction("ERROR", HybrisIcons.Log.Level.ERROR)
class FatalLoggerAction : AbstractLoggerAction("FATAL", HybrisIcons.Log.Level.FATAL)
class SevereLoggerAction : AbstractLoggerAction("SEVERE", HybrisIcons.Log.Level.SEVERE)