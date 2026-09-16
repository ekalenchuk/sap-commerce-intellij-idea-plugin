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

package sap.commerce.toolset.beanSystem.meta

import com.intellij.openapi.project.Project
import com.intellij.util.xml.DomFileDescription
import sap.commerce.toolset.HybrisConstants
import sap.commerce.toolset.beanSystem.BSDomFileDescription
import sap.commerce.toolset.meta.MetaModelTrackerProvider

class BSMetaModelTrackerProvider : MetaModelTrackerProvider {

    override fun getTracker(project: Project) = BSModificationTracker.getInstance(project)
    override fun isTracked(domFileDescription: DomFileDescription<*>) = domFileDescription is BSDomFileDescription
    override fun createKeyResolver(project: Project): (String, String) -> String? = { fileName, _ ->
        fileName.takeIf { it.endsWith(HybrisConstants.HYBRIS_BEANS_XML_FILE_ENDING) }
    }
}
