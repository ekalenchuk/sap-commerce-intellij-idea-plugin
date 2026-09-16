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

package sap.commerce.toolset.cockpitNG.meta

import com.intellij.openapi.project.Project
import com.intellij.util.xml.DomFileDescription
import sap.commerce.toolset.cockpitNG.*
import sap.commerce.toolset.meta.MetaModelTrackerProvider

class CngMetaModelTrackerProvider : MetaModelTrackerProvider {

    override fun getTracker(project: Project) = CngModificationTracker.getInstance(project)

    override fun isTracked(domFileDescription: DomFileDescription<*>) = when (domFileDescription) {
        is CngConfigDomFileDescription,
        is CngWidgetsDomFileDescription,
        is CngActionDefinitionDomFileDescription,
        is CngEditorDefinitionDomFileDescription,
        is CngWidgetDefinitionDomFileDescription -> true

        else -> false
    }

    /**
     * For CockpitNG only already tracked models are considered, the tracker key is the file path.
     */
    override fun createKeyResolver(project: Project): (String, String) -> String? {
        val trackedModels by lazy { CngMetaModelStateService.getInstance(project).getTrackedModels() }

        return { _, path -> path.takeIf { trackedModels.contains(it) } }
    }
}
