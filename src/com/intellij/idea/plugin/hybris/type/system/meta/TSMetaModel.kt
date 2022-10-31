/*
 * This file is part of "SAP Commerce Developers Toolset" plugin for Intellij IDEA.
 * Copyright (C) 2019 EPAM Systems <hybrisideaplugin@epam.com>
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
package com.intellij.idea.plugin.hybris.type.system.meta

import com.intellij.idea.plugin.hybris.type.system.meta.impl.CaseInsensitive
import com.intellij.idea.plugin.hybris.type.system.meta.model.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.intellij.util.containers.MultiMap
import com.intellij.util.xml.DomElement
import java.util.concurrent.ConcurrentHashMap

class TSMetaModel(
    val module: Module,
    val psiFile: PsiFile,
    val custom: Boolean
) : Disposable {

    private val myMetaCache: MutableMap<MetaType, MultiMap<String, TSMetaClassifier<DomElement>>> = ConcurrentHashMap()
    private val myReferencesBySourceTypeName = CaseInsensitive.CaseInsensitiveConcurrentHashMap<String, TSMetaRelation.TSMetaRelationElement>()

    fun addMetaModel(meta: TSMetaClassifier<out DomElement>, metaType: MetaType) {
        // add log why no name
        if (meta.name == null) return

        getMetaType<TSMetaClassifier<out DomElement>>(metaType).putValue(meta.name!!.lowercase(), meta)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : TSMetaClassifier<out DomElement>> getMetaType(metaType: MetaType): MultiMap<String, T> =
        myMetaCache.computeIfAbsent(metaType) { MultiMap.createLinked() } as MultiMap<String, T>

    fun getMetaTypes() = myMetaCache;

    fun getReference(name: String?): TSMetaRelation.TSMetaRelationElement? = name?.let { getReferences()[it] }

    fun getReferences() = myReferencesBySourceTypeName;


    override fun dispose() {
        myMetaCache.clear()
    }

    override fun toString() = "Module: ${module.name} | psi file: ${psiFile.name}"
}