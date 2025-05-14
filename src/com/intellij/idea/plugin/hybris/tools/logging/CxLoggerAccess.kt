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

package com.intellij.idea.plugin.hybris.tools.logging

import com.intellij.idea.plugin.hybris.notifications.Notifications
import com.intellij.idea.plugin.hybris.tools.logging.actions.CxLoggerCacheTracker
import com.intellij.idea.plugin.hybris.tools.remote.RemoteConnectionType
import com.intellij.idea.plugin.hybris.tools.remote.RemoteConnectionUtil
import com.intellij.idea.plugin.hybris.tools.remote.http.AbstractHybrisHacHttpClient
import com.intellij.idea.plugin.hybris.tools.remote.http.HybrisHacHttpClient
import com.intellij.javascript.nodejs.execution.withBackgroundProgress
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import kotlinx.coroutines.runBlocking

private const val FETCH_LOGGERS_STATE_GROOVY_SCRIPT = """
    import de.hybris.platform.core.Registry
    import de.hybris.platform.hac.facade.HacLog4JFacade
    import java.util.stream.Collectors
    
    Registry.applicationContext.getBean("hacLog4JFacade", HacLog4JFacade.class).getLoggers().stream()
            .map { it -> it.name + " | " + it.parentName + " | " + it.effectiveLevel }
            .collect(Collectors.joining("\n"))
"""

@Service(Service.Level.PROJECT)
class CxLoggerAccess(private val project: Project) {
    companion object {
        fun getInstance(project: Project): CxLoggerAccess = project.getService(CxLoggerAccess::class.java)
    }

    fun getLoggers(): Map<String, CxLoggerModel> {
        return CachedValuesManager.getManager(project).getCachedValue<Map<String, CxLoggerModel>>(
            project,
            {
                val localCache: Map<String, CxLoggerModel> = runBlocking {
                    withBackgroundProgress(project, "Loading loggers from SAP Commerce...") {
                        try {
                            val groovyScriptResult = HybrisHacHttpClient.getInstance(project).executeGroovyScript(
                                project, FETCH_LOGGERS_STATE_GROOVY_SCRIPT.trimIndent(), false, AbstractHybrisHacHttpClient.DEFAULT_HAC_TIMEOUT
                            )

                            val server = RemoteConnectionUtil.getActiveRemoteConnectionSettings(project, RemoteConnectionType.Hybris)

                            if (groovyScriptResult.statusCode == 200) {
                                Notifications
                                    .create(
                                        NotificationType.INFORMATION,
                                        "Loading loggers from SAP Commerce...",
                                        """
                                        <p>Loggers state is fetched.</p>
                                        <p>${server.shortenConnectionName()}</p>
                                    """.trimIndent()
                                    )
                                    .hideAfter(5)
                                    .notify(project)
                            } else {
                                Notifications
                                    .create(
                                        NotificationType.ERROR,
                                        "Loading loggers from SAP Commerce...",
                                        """
                                            <p>Failed to fetch logger states.</p>
                                            <p>${server.shortenConnectionName()}</p>
                                        """.trimIndent()
                                    )
                                    .hideAfter(5)
                                    .notify(project)
                            }
                            val resultAsString = groovyScriptResult.result

                            return@withBackgroundProgress resultAsString
                                .splitToSequence("\n")
                                .map { it -> it.split(" | ") }
                                .map { it ->
                                    if (it.size == 3)
                                        CxLoggerModel(it[0], it[2], it[1])
                                    else {
                                        CxLoggerModel(null, null, null)
                                    }
                                }
                                .filter { it.name != null }
                                .associateBy { it.name!! }
                        } catch (e: Exception) {
                            return@withBackgroundProgress mutableMapOf()
                        }
                    }
                }

                CachedValueProvider.Result.create(localCache, CxLoggerCacheTracker)
            }
        )
    }

    fun refresh() = CxLoggerCacheTracker.incModificationCount()
}