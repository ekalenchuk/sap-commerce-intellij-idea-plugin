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
package com.intellij.idea.plugin.hybris.system.bean.meta

import com.intellij.idea.plugin.hybris.system.bean.meta.impl.BSMetaModelBuilder
import com.intellij.idea.plugin.hybris.system.bean.model.Beans
import com.intellij.idea.plugin.hybris.system.meta.MetaModelProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class BSMetaModelProcessor(project: Project) : MetaModelProcessor<Beans, BSMetaModel>(project) {

    override fun process(container: String, yContainer: String, fileName: String, custom: Boolean, dom: Beans): BSMetaModel =
        with(BSMetaModelBuilder(container, yContainer, fileName, custom)) {
            withEnumTypes(dom.enums)
            withBeanTypes(dom.beans)
            withEventTypes(dom.beans)
            build()
        }

    companion object {
        fun getInstance(project: Project): BSMetaModelProcessor = project.service()
    }
}