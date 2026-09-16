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

package sap.commerce.toolset.meta

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.xml.DomFileDescription

/**
 * Binds a meta-model modification tracker to the files it tracks.
 * Used by the project-wide VFS and PSI listeners and on project startup.
 */
interface MetaModelTrackerProvider {

    fun getTracker(project: Project): MetaModelModificationTracker

    fun isTracked(domFileDescription: DomFileDescription<*>): Boolean

    /**
     * Resolver of the tracker key for a changed file (name and path) or `null` when the file is not tracked.
     * Created once per project and batch of VFS events.
     */
    fun createKeyResolver(project: Project): (fileName: String, path: String) -> String?

    companion object {
        val EP = ExtensionPointName.create<MetaModelTrackerProvider>("sap.commerce.toolset.meta.modelTrackerProvider")
    }
}
