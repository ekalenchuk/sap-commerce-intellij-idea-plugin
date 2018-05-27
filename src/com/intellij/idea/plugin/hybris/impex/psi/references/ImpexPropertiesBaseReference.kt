/*
 * This file is part of "hybris integration" plugin for Intellij IDEA.
 * Copyright (C) 2014-2016 Alexander Bartash <AlexanderBartash@gmail.com>
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

package com.intellij.idea.plugin.hybris.impex.psi.references

import com.intellij.lang.properties.PropertiesImplUtil
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.ElementManipulator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.util.IncorrectOperationException

/**
 * @author Nosov Aleksandr <nosovae.dev@gmail.com>
 */
class ImpexPropertiesBaseReference(private val prev: PsiElement?, current: PsiElement) : PsiReferenceBase.Poly<PsiElement>(current, false) {
    private val NO_VARIANTS = arrayOfNulls<Any>(0)

    override fun getVariants() = NO_VARIANTS

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val project = element.project
        val key = getKey(prev)
        val properties = PropertiesImplUtil.findPropertiesByKey(project, key)
        if (properties.isNotEmpty()) {
            return PsiElementResolveResult.createResults(properties.map { it.psiElement })
        }
        return ResolveResult.EMPTY_ARRAY
    }

    private fun getKey(prev: PsiElement?) =
            if (prev != null)
                prev.text.replace("\$config", "") + element.text.replace("-", "")
            else
                element.text.replace("-", "")


    override fun getRangeInElement() = TextRange.from(1, element.textLength)


    @Throws(IncorrectOperationException::class)
    override fun handleElementRename(newElementName: String) =
            getManipulator().handleContentChange(myElement, rangeInElement, newElementName)


    private fun getManipulator(): ElementManipulator<PsiElement> {
        return object : AbstractElementManipulator<PsiElement>() {
            override fun handleContentChange(element: PsiElement, range: TextRange, newContent: String) = null
        }
    }
}