/*
 * This file is part of "SAP Commerce Developers Toolset" plugin for Intellij IDEA.
 * Copyright (C) 2019-2023 EPAM Systems <hybrisideaplugin@epam.com> and contributors
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
package com.intellij.idea.plugin.hybris.impex.highlighting

import com.intellij.codeHighlighting.RainbowHighlighter
import com.intellij.idea.plugin.hybris.common.HybrisConstants
import com.intellij.idea.plugin.hybris.common.utils.HybrisIcons
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class ImpexColorSettingsPage : ColorSettingsPage {

    override fun getIcon(): Icon = HybrisIcons.IMPEX_FILE
    override fun getHighlighter() = DefaultImpexSyntaxHighlighter.instance
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = customTags
    override fun getAttributeDescriptors() = descriptors
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getDisplayName() = HybrisConstants.IMPEX

    override fun getDemoText(): String {
        return """# Comment
   
${"$"}START_USERRIGHTS
Type      ; UID        ; MemberOfGroups ; Password ; Target       ; read ; change ; create ; delete ; change_perm
UserGroup ; impexgroup ; employeegroup  ;
          ;            ;                ;          ; Product.code ; +    ; +      ; +      ; +      ; -
Customer  ; impex-demo ; impexgroup     ; 1234     ;              ; $inheritedPermission    ; -      ;        ; $inheritedPermission      ;
${"$"}END_USERRIGHTS

${"$"}lang = en
${"$"}configProperty = ${"$"}config-HYBRIS_BIN_DIR
${"$"}contentCatalog = projectContentCatalog
${"$"}contentCV = catalogVersion(CatalogVersion.catalog(Catalog.id[default = ${"$"}contentCatalog]), CatalogVersion.version[default = 'Staged'])[default = ${"$"}contentCatalog:Staged]
${"$"}macro = qwe;qwe, qwe, ;qwe

#% impex.setLocale( Locale.GERMAN );

INSERT SomeType; param; param2; param3
<vlo>; value; value; another value</vlo>
<vle>; value; value; another value</vle>
<vlo>; value; value; another value</vlo>
<vle>; value; value; another value</vle>

INSERT_UPDATE SomeType; ${"$"}contentCV[unique = true][map-delimiter = |][dateformat = yyyy-MM-dd HH:mm:ss]; uid[unique = true]; title[lang = ${"$"}lang]; ${attributeHeaderAbbreviation("C@someAttribute")}
Subtype ; ; account                ; "Your Account"
        ; ; <ignore>               ; "Add/Edit Address"
        ; ; key -> vaue | key ->
vaue                               ; "Address Book"
        ; ; value1, value2, value3 ; 12345 ; com.domain.Class ; qwe : asd

INSERT Address[impex.legacy.mode = true, batchmode = true]; firstname; owner(Principal.uid | AbstractOrder.code); Hans; admin

UPDATE Address; firstname; owner(Principal.uid | AbstractOrder.code); &docId
; Hans ; admin ; id

remove Address; firstname; owner(Principal.uid | AbstractOrder.code); Hans; admin

INSERT_UPDATE Media; @media[translator = de.hybris.platform.impex.jalo.media.MediaDataTranslator]; mime[default = 'image/png']
; ; ${"$"}contentResource/images/logo.png

@@@@@
"""
    }

    private val customTags = with (RainbowHighlighter.createRainbowHLM()) {
        put("permission_inherited", ImpexHighlighterColors.USER_RIGHTS_PERMISSION_INHERITED)
        put("attribute_header_abbreviation", ImpexHighlighterColors.ATTRIBUTE_HEADER_ABBREVIATION)
        put("vle", ImpexHighlighterColors.VALUE_LINE_EVEN)
        put("vlo", ImpexHighlighterColors.VALUE_LINE_ODD)
        this
    }
    private val inheritedPermission = "<permission_inherited>.</permission_inherited>"
    private fun attributeHeaderAbbreviation(abbreviation : String) = "<attribute_header_abbreviation>${"$$abbreviation"}</attribute_header_abbreviation>"

    private val descriptors = arrayOf(
        AttributesDescriptor("Comment line", ImpexHighlighterColors.PROPERTY_COMMENT),

        AttributesDescriptor("Macro//Name declaration", ImpexHighlighterColors.MACRO_NAME_DECLARATION),
        AttributesDescriptor("Macro//Value", ImpexHighlighterColors.MACRO_VALUE),
        AttributesDescriptor("Macro//Usage", ImpexHighlighterColors.MACRO_USAGE),
        AttributesDescriptor("Macro//Assign value", ImpexHighlighterColors.ASSIGN_VALUE),

        AttributesDescriptor("Mode//Insert", ImpexHighlighterColors.HEADER_MODE_INSERT),
        AttributesDescriptor("Mode//Update", ImpexHighlighterColors.HEADER_MODE_UPDATE),
        AttributesDescriptor("Mode//Insert or update", ImpexHighlighterColors.HEADER_MODE_INSERT_UPDATE),
        AttributesDescriptor("Mode//Remove", ImpexHighlighterColors.HEADER_MODE_REMOVE),

        AttributesDescriptor("Type//Header type", ImpexHighlighterColors.HEADER_TYPE),
        AttributesDescriptor("Type//Value sub-type", ImpexHighlighterColors.VALUE_SUBTYPE),

        AttributesDescriptor("Separator//Field value", ImpexHighlighterColors.FIELD_VALUE_SEPARATOR),
        AttributesDescriptor("Separator//List item", ImpexHighlighterColors.FIELD_LIST_ITEM_SEPARATOR),

        AttributesDescriptor("Value line//Even", ImpexHighlighterColors.VALUE_LINE_EVEN),
        AttributesDescriptor("Value line//Odd", ImpexHighlighterColors.VALUE_LINE_ODD),

        AttributesDescriptor("Value//Field value", ImpexHighlighterColors.FIELD_VALUE),
        AttributesDescriptor("Value//Single string", ImpexHighlighterColors.SINGLE_STRING),
        AttributesDescriptor("Value//Double string", ImpexHighlighterColors.DOUBLE_STRING),
        AttributesDescriptor("Value//Ignore value", ImpexHighlighterColors.FIELD_VALUE_IGNORE),
        AttributesDescriptor("Value//Boolean", ImpexHighlighterColors.BOOLEAN),
        AttributesDescriptor("Value//Digit", ImpexHighlighterColors.DIGIT),

        AttributesDescriptor("Bean shell//Marker", ImpexHighlighterColors.BEAN_SHELL_MARKER),
        AttributesDescriptor("Bean shell//Body", ImpexHighlighterColors.BEAN_SHELL_BODY),

        AttributesDescriptor("Brackets//Square brackets", ImpexHighlighterColors.SQUARE_BRACKETS),
        AttributesDescriptor("Brackets//Round brackets", ImpexHighlighterColors.ROUND_BRACKETS),

        AttributesDescriptor("Attribute//Name", ImpexHighlighterColors.ATTRIBUTE_NAME),
        AttributesDescriptor("Attribute//Value", ImpexHighlighterColors.ATTRIBUTE_VALUE),
        AttributesDescriptor("Attribute//Separator", ImpexHighlighterColors.ATTRIBUTE_SEPARATOR),
        AttributesDescriptor("Attribute//Header abbreviation", ImpexHighlighterColors.ATTRIBUTE_HEADER_ABBREVIATION),

        AttributesDescriptor("Parameter//Document id", ImpexHighlighterColors.DOCUMENT_ID),
        AttributesDescriptor("Parameter//Parameter name", ImpexHighlighterColors.HEADER_PARAMETER_NAME),
        AttributesDescriptor("Parameter//Special parameter name", ImpexHighlighterColors.HEADER_SPECIAL_PARAMETER_NAME),
        AttributesDescriptor("Parameter//Parameters separator", ImpexHighlighterColors.PARAMETERS_SEPARATOR),
        AttributesDescriptor("Parameter//Function call", ImpexHighlighterColors.FUNCTION_CALL),

        AttributesDescriptor("Alternative map delimiter", ImpexHighlighterColors.ALTERNATIVE_MAP_DELIMITER),
        AttributesDescriptor("Default key-value delimiter", ImpexHighlighterColors.DEFAULT_KEY_VALUE_DELIMITER),
        AttributesDescriptor("Default path delimiter", ImpexHighlighterColors.DEFAULT_PATH_DELIMITER),
        AttributesDescriptor("Comma", ImpexHighlighterColors.COMMA),
        AttributesDescriptor("Alternative pattern", ImpexHighlighterColors.ALTERNATIVE_PATTERN),
        AttributesDescriptor("Bad character", HighlighterColors.BAD_CHARACTER),
        AttributesDescriptor("Warnings", ImpexHighlighterColors.WARNINGS_ATTRIBUTES),

        AttributesDescriptor("User rights", ImpexHighlighterColors.USER_RIGHTS),
        AttributesDescriptor("User rights//Parameter name", ImpexHighlighterColors.USER_RIGHTS_HEADER_PARAMETER),
        AttributesDescriptor("User rights//Mandatory parameter name", ImpexHighlighterColors.USER_RIGHTS_HEADER_MANDATORY_PARAMETER),
        AttributesDescriptor("User rights//Permission allowed", ImpexHighlighterColors.USER_RIGHTS_PERMISSION_ALLOWED),
        AttributesDescriptor("User rights//Permission denied", ImpexHighlighterColors.USER_RIGHTS_PERMISSION_DENIED),
        AttributesDescriptor("User rights//Permission inherited", ImpexHighlighterColors.USER_RIGHTS_PERMISSION_INHERITED)
    )

}
