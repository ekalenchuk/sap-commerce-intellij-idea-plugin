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
package sap.commerce.toolset.angular.project.configurator

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.DirectoryProjectConfigurator
import sap.commerce.toolset.angular.AngularConstants
import sap.commerce.toolset.angular.project.descriptor.AngularModuleDescriptor
import sap.commerce.toolset.project.configurator.ProjectPostImportConfigurator
import sap.commerce.toolset.project.context.ProjectPostImportContext

class AngularConfigurator : ProjectPostImportConfigurator {

    override val name: String
        get() = "Angular"

    override suspend fun configure(context: ProjectPostImportContext) {
        val project = context.project
            .takeUnless { it.isDisposed }
            ?: return

        val angularModuleDescriptors = context.chosenOtherModuleDescriptors
            .filterIsInstance<AngularModuleDescriptor>()
            .takeIf { it.isNotEmpty() }
            ?: return

        val modulesToCreate = readAction {
            angularModuleDescriptors.mapNotNull {
                val vfs = VfsUtil.findFile(it.moduleRootPath, true)
                    ?: return@mapNotNull null
                val moduleRef = ModuleManager.getInstance(project).findModuleByName(it.ideaModuleName())
                    ?.let { module -> Ref.create(module) }
                    ?: return@mapNotNull null

                vfs to moduleRef
            }
        }

        // Angular plugin API is located in its internal module, use its registered extension instead
        val angularProjectConfigurator = DIRECTORY_PROJECT_CONFIGURATOR_EP.extensionList
            .find { it.javaClass.name == AngularConstants.PROJECT_CONFIGURATOR }
            ?: return

        runInEdt {
            modulesToCreate.forEach {
                angularProjectConfigurator.configureProject(project, it.first, it.second, true)
            }
        }
    }

    companion object {
        private val DIRECTORY_PROJECT_CONFIGURATOR_EP = ExtensionPointName.create<DirectoryProjectConfigurator>("com.intellij.directoryProjectConfigurator")
    }
}
