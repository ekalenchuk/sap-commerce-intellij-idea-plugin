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

package sap.commerce.toolset.project.configurator

import com.intellij.facet.FacetType
import com.intellij.facet.FacetTypeId
import com.intellij.facet.FacetTypeRegistry
import com.intellij.framework.detection.DetectionExcludesConfiguration
import com.intellij.framework.detection.impl.FrameworkDetectionUtil
import com.intellij.openapi.project.Project

abstract class ExcludeFrameworkDetectionConfigurator : ProjectImportConfigurator {

    override val name: String
        get() = "Exclude Framework Detection"

    protected fun excludeFrameworkDetection(project: Project, facetTypeId: FacetTypeId<*>) = excludeFrameworkDetection(
        project,
        FacetTypeRegistry.getInstance().findFacetType(facetTypeId)
    )

    /**
     * Facet type is resolved by its string id, when the facet type class is not accessible from a content module.
     */
    protected fun excludeFrameworkDetection(project: Project, facetTypeId: String) = excludeFrameworkDetection(
        project,
        FacetTypeRegistry.getInstance().findFacetType(facetTypeId)
    )

    private fun excludeFrameworkDetection(project: Project, facetType: FacetType<*, *>?) = facetType
        ?.let { FrameworkDetectionUtil.findFrameworkTypeForFacetDetector(it) }
        ?.let { DetectionExcludesConfiguration.getInstance(project).addExcludedFramework(it) }
}
