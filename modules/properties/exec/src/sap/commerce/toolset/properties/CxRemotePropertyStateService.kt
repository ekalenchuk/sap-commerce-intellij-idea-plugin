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

package sap.commerce.toolset.properties

import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.apache.http.HttpStatus
import sap.commerce.toolset.Notifications
import sap.commerce.toolset.exec.context.DefaultExecResult
import sap.commerce.toolset.hac.exec.HacExecConnectionService
import sap.commerce.toolset.hac.exec.settings.event.HacConnectionSettingsListener
import sap.commerce.toolset.hac.exec.settings.state.HacConnectionSettingsState
import sap.commerce.toolset.properties.exec.CxRemotePropertyClient
import sap.commerce.toolset.properties.exec.CxRemotePropertyState
import sap.commerce.toolset.properties.exec.CxRemotePropertyStatePage
import sap.commerce.toolset.properties.exec.event.CxRemotePropertyStateListener
import sap.commerce.toolset.properties.presentation.CxPropertyPresentation
import java.util.*

@Service(Service.Level.PROJECT)
class CxRemotePropertyStateService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) : Disposable {

    private val propertyStates = Collections.synchronizedMap(WeakHashMap<String, CxRemotePropertyState>())
    private val fetchingConnections = Collections.synchronizedSet(mutableSetOf<String>())

    val ready: Boolean
        get() = fetchingConnections.isEmpty()

    val stateInitialized: Boolean
        get() = state(HacExecConnectionService.getInstance(project).activeConnection.uuid).initialized()

    fun isFetching(server: HacConnectionSettingsState): Boolean = fetchingConnections.contains(server.uuid)

    init {
        with(project.messageBus.connect(this)) {
            subscribe(HacConnectionSettingsListener.TOPIC, object : HacConnectionSettingsListener {
                override fun onSave(settings: Collection<HacConnectionSettingsState>) = settings.forEach { clearState(it) }
            })
        }
    }

    fun fetch() = fetch(HacExecConnectionService.getInstance(project).activeConnection)

    /**
     * Reloads the current accumulated view from page 1. Used by the manual "Fetch" action
     * and by the post-mutation refresh helper [refetchLoaded].
     */
    fun fetch(server: HacConnectionSettingsState) {
        val current = state(server.uuid).get()
        // After a mutation we want to keep what the user has already scrolled into view.
        // pageSize is set to the larger of the configured page size and the loaded count
        // so a single fetch rehydrates the entire visible window in one request.
        val pageSize = current?.let { maxOf(it.pageSize, it.loadedCount) } ?: CxPropertyConstants.DEFAULT_PAGE_SIZE
        fetch(
            server = server,
            page = 1,
            pageSize = pageSize,
            keyFilter = current?.keyFilter.orEmpty(),
            valueFilter = current?.valueFilter.orEmpty(),
            append = false,
        )
    }

    /** Loads the next page and appends it to the current accumulated list. */
    fun fetchNextPage(server: HacConnectionSettingsState) {
        val current = state(server.uuid).get() ?: return
        if (!current.hasMore) return
        fetch(
            server = server,
            page = current.lastLoadedPage + 1,
            pageSize = current.pageSize,
            keyFilter = current.keyFilter,
            valueFilter = current.valueFilter,
            append = true,
        )
    }

    /**
     * Starts a fresh accumulated view with the given filter / page size. Always loads
     * page 1 and replaces whatever is currently stored.
     */
    fun resetAndFetch(
        server: HacConnectionSettingsState,
        pageSize: Int = CxPropertyConstants.DEFAULT_PAGE_SIZE,
        keyFilter: String = "",
        valueFilter: String = "",
    ) = fetch(
        server = server,
        page = 1,
        pageSize = pageSize,
        keyFilter = keyFilter,
        valueFilter = valueFilter,
        append = false,
    )

    /**
     * Loads every property of [server] in a single request and hands the complete snapshot over.
     *
     * The accumulated state only holds the pages scrolled into view, which is rarely the whole instance - anything
     * choosing from "all of its properties" has to ask for them first.
     */
    fun fetchAll(server: HacConnectionSettingsState, onLoaded: (CxRemotePropertyStatePage) -> Unit) {
        val loaded = state(server.uuid).get()

        if (loaded != null && !loaded.hasMore && loaded.keyFilter.isEmpty() && loaded.valueFilter.isEmpty()) {
            onLoaded(loaded)
            return
        }

        fetch(
            server = server,
            page = 1,
            pageSize = maxOf(loaded?.totalItems ?: 0, CxPropertyConstants.DEFAULT_PAGE_SIZE),
            keyFilter = "",
            valueFilter = "",
            append = false,
            onLoaded = onLoaded,
        )
    }

    private fun fetch(
        server: HacConnectionSettingsState,
        page: Int,
        pageSize: Int,
        keyFilter: String,
        valueFilter: String,
        append: Boolean,
        onLoaded: ((CxRemotePropertyStatePage) -> Unit)? = null,
    ) {
        fetchingConnections.add(server.uuid)
        project.messageBus.syncPublisher(CxRemotePropertyStateListener.TOPIC).onPropertiesStateChanged(server)

        coroutineScope.launch {
            val newPage = runCatching {
                CxRemotePropertyClient.getInstance(project).fetch(server, page, pageSize, keyFilter, valueFilter)
            }

            val loaded = newPage.getOrNull()

            if (loaded == null) {
                clearState(server)
                notify(NotificationType.ERROR, "Failed to retrieve properties") {
                    newPage.exceptionOrNull()?.message ?: "Unable to retrieve properties state."
                }
            } else {
                val state = state(server.uuid)
                if (append) state.append(loaded) else state.replace(loaded)
                fetchingConnections.remove(server.uuid)
                project.messageBus.syncPublisher(CxRemotePropertyStateListener.TOPIC).onPropertiesStateChanged(server)
                state.get()?.let { onLoaded?.invoke(it) }
                // No success notification — the data lands in the panel's bottom toolbar
                // ("Loaded N of M total") which is sufficient feedback. The fetch errors
                // above still surface because failure is non-obvious.
            }
        }
    }

    fun upsertProperty(key: String, value: String, callback: (Boolean) -> Unit = {}) {
        upsertProperty(HacExecConnectionService.getInstance(project).activeConnection, key, value, callback)
    }

    fun upsertProperty(server: HacConnectionSettingsState, key: String, value: String, callback: (Boolean) -> Unit = {}) {
        val trimmedKey = key.trim()
        if (!CxRemotePropertyClient.isValidPropertyKey(trimmedKey)) {
            callback(false)
            return
        }

        coroutineScope.launch {
            val outcome = CxRemotePropertyClient.getInstance(project).upsert(server, trimmedKey, value)

            if (outcome.success) {
                notify(NotificationType.INFORMATION, "Property stored") {
                    "<p>Property: $trimmedKey</p><p>Server: ${server.shortenConnectionName}</p>"
                }
                refetchLoaded(server)
                callback(true)
            } else {
                notify(NotificationType.ERROR, "Failed to store property") {
                    "<p>${outcome.reason}</p><p>Server: ${server.shortenConnectionName}</p>"
                }
                callback(false)
            }
        }
    }

    fun applyProperties(
        properties: List<CxPropertyPresentation>,
        callback: (CoroutineScope, DefaultExecResult) -> Unit = { _, _ -> },
    ) = applyProperties(HacExecConnectionService.getInstance(project).activeConnection, properties, callback)

    fun applyProperties(
        server: HacConnectionSettingsState,
        properties: List<CxPropertyPresentation>,
        callback: (CoroutineScope, DefaultExecResult) -> Unit = { _, _ -> },
    ) {
        coroutineScope.launch {
            // Every property is attempted even when an earlier one fails, so a single rejected
            // key cannot silently leave the rest of the template unapplied.
            val client = CxRemotePropertyClient.getInstance(project)
            val failed = properties.filterNot { client.upsert(server, it.key, it.value).success }

            refetchLoaded(server)

            val result = if (failed.isEmpty()) {
                notify(NotificationType.INFORMATION, "Properties applied") {
                    "<p>Applied properties: ${properties.size}</p><p>Server: ${server.shortenConnectionName}</p>"
                }
                DefaultExecResult()
            } else {
                val failedKeys = failed.joinToString { it.key }
                notify(NotificationType.ERROR, "Failed to apply properties") {
                    "<p>Applied properties: ${properties.size - failed.size} of ${properties.size}</p>" +
                        "<p>Failed properties: $failedKeys</p>" +
                        "<p>Server: ${server.shortenConnectionName}</p>"
                }
                DefaultExecResult(
                    statusCode = HttpStatus.SC_BAD_REQUEST,
                    errorMessage = "Failed to apply properties: $failedKeys",
                )
            }

            callback(coroutineScope, result)
        }
    }

    fun deleteProperty(key: String, callback: (Boolean) -> Unit = {}) {
        deleteProperty(HacExecConnectionService.getInstance(project).activeConnection, key, callback)
    }

    fun deleteProperty(server: HacConnectionSettingsState, key: String, callback: (Boolean) -> Unit = {}) {
        val trimmedKey = key.trim()

        coroutineScope.launch {
            val outcome = CxRemotePropertyClient.getInstance(project).delete(server, trimmedKey)

            if (outcome.success) {
                notify(NotificationType.INFORMATION, "Property deleted") {
                    "<p>Property: $trimmedKey</p><p>Server: ${server.shortenConnectionName}</p>"
                }
                refetchLoaded(server)
                callback(true)
            } else {
                notify(NotificationType.ERROR, "Failed to delete property") {
                    "<p>${outcome.reason}</p><p>Server: ${server.shortenConnectionName}</p>"
                }
                callback(false)
            }
        }
    }

    fun state(settingsUUID: String): CxRemotePropertyState = propertyStates.computeIfAbsent(settingsUUID) { CxRemotePropertyState() }

    private fun clearState(server: HacConnectionSettingsState) {
        propertyStates[server.uuid]?.clear()
        fetchingConnections.remove(server.uuid)
        project.messageBus.syncPublisher(CxRemotePropertyStateListener.TOPIC).onPropertiesStateChanged(server)
    }

    private fun refetchLoaded(server: HacConnectionSettingsState) = fetch(server)

    private fun notify(type: NotificationType, title: String, contentProvider: () -> String) = Notifications
        .create(type, title, contentProvider())
        .hideAfter(5)
        .notify(project)

    override fun dispose() = propertyStates.clear()

    companion object {
        fun getInstance(project: Project): CxRemotePropertyStateService = project.service()
    }
}
