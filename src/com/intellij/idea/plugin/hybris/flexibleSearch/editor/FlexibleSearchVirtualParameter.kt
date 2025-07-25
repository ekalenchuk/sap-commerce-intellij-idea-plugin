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

package com.intellij.idea.plugin.hybris.flexibleSearch.editor

import com.intellij.idea.plugin.hybris.flexibleSearch.psi.FlexibleSearchBindParameter
import com.intellij.idea.plugin.hybris.flexibleSearch.psi.FlexibleSearchTypes
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import com.intellij.util.asSafely
import org.apache.commons.lang3.BooleanUtils
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.KClass

data class FlexibleSearchVirtualParameter(
    val name: String,
    val operand: IElementType? = null,
    private val rawType: String? = null,
    val displayName: String = StringUtil.shortenPathWithEllipsis(name, 20),
) {

    private val lazySqlValue = ClearableLazyValue.create<String> { this.evaluateSqlValue() }
    private val lazyPresentationValue = ClearableLazyValue.create<String> { this.evaluatePresentationValue() }

    val type: KClass<*> = when (rawType) {
        "boolean", "java.lang.Boolean" -> Boolean::class
        "String", "java.lang.String", "localized:java.lang.String" -> String::class
        "byte", "java.lang.Byte" -> Byte::class
        "short", "java.lang.Short" -> Short::class
        "int", "java.lang.Integer" -> Int::class
        "float", "java.lang.Float" -> Float::class
        "double", "java.lang.Double" -> Double::class
        "long", "java.lang.Long" -> Long::class
        "java.util.Date" -> Date::class

        else -> Any::class
    }

    var rawValue: Any? = null
        set(value) {
            field = value
            lazySqlValue.drop()
            lazyPresentationValue.drop()
        }

    val sqlValue: String get() = lazySqlValue.get()
    val presentationValue: String get() = lazyPresentationValue.get()

    private fun evaluateSqlValue(): String = when (type) {
        Boolean::class -> rawValue?.asSafely<Boolean>()?.takeIf { it }
            ?.let { "1" }
            ?: "0"

        Date::class -> rawValue?.asSafely<Date>()?.time?.toString()
            ?: ""

        String::class -> rawValue?.asSafely<String>()
            ?.replace("'", "''")
            ?.let { stringValue ->
                if (operand == FlexibleSearchTypes.IN_EXPRESSION) stringValue
                    .split("\n")
                    .filter { it.isNotBlank() }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(",") { "'$it'" }
                else "'$stringValue'"
            }
            ?: "''"

        else -> rawValue?.asSafely<String>()
            ?.let { plainValue ->
                if (operand == FlexibleSearchTypes.IN_EXPRESSION) plainValue
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(",")
                else plainValue
            }
            ?: ""
    }

    private fun evaluatePresentationValue(): String = when (type) {
        Boolean::class -> BooleanUtils.toStringTrueFalse(sqlValue == "1")

        Date::class -> rawValue
            ?.asSafely<Date>()
            ?.let { SimpleDateFormat(DATE_FORMAT).format(it) }
            ?: ""

        else -> sqlValue
    }

    companion object {

        const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"

        fun of(bindParameter: FlexibleSearchBindParameter, currentParameters: Map<String, FlexibleSearchVirtualParameter>) = FlexibleSearchVirtualParameter(
            name = bindParameter.value,
            operand = bindParameter.expression?.elementType,
            rawType = bindParameter.itemType,
        ).apply {
            rawValue = currentParameters[name]?.rawValue
        }

    }
}