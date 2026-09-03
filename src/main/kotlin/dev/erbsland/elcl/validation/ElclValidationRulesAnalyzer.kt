package dev.erbsland.elcl.validation

/** Applies ELCL-VR document rules to the recoverable section model. */
internal class ElclValidationRulesAnalyzer {
    /** Appends validation-rule diagnostics without changing parsed document state. */
    fun validate(state: AnalysisState) {
        val templates = state.sections.filter { it.path.firstOrNull() == "vr_template" && it.path.size == 2 }
            .associateBy { it.path[1] }
        val namedIndexes = linkedMapOf<String, RuleSection>()
        state.sections.filter { it.path.lastOrNull() == "vr_key" }.forEach { index ->
            index.fields["name"]?.let { field ->
                val name = normalize(firstValue(field.value).orEmpty())
                if (name.isEmpty()) state.error(field.valueRange, "Index name must be a non-empty ELCL name")
                else if (namedIndexes.putIfAbsent(name, index) != null) state.error(field.valueRange, "Duplicate normalized index name '$name'")
            }
        }
        state.sections.forEach { section ->
            val reserved = section.path.filter { it.startsWith("vr_") }
            reserved.filter { it !in RESERVED_NAMES && !it.startsWith("vr_vr_") }.forEach {
                state.error(section.range, "Unknown reserved validation-rules name '$it'; use 'vr_$it' to escape a literal vr_ name")
            }
            if ("vr_template" in section.path && section.path.firstOrNull() != "vr_template") {
                state.error(section.range, "vr_template is only valid at the document root")
            }
            if (section.path.lastOrNull() == "vr_any" && section.path.size == 1) {
                state.error(section.range, "vr_any requires a parent section rule")
            }
            when (section.path.lastOrNull()) {
                "vr_dependency" -> validateDependency(state, section)
                "vr_key" -> validateIndex(state, section)
                else -> validateNodeRule(state, section, templates, namedIndexes.keys)
            }
        }
        validateAlternatives(state)
    }

    private fun validateAlternatives(state: AnalysisState) {
        state.sections.groupBy { it.path }.filterValues { it.size > 1 }.forEach { (_, alternatives) ->
            if (alternatives.any { !it.list }) {
                alternatives.drop(1).forEach { state.error(it.range, "Multiple definitions for one node must all use section-list alternative syntax") }
                return@forEach
            }
            val defaults = alternatives.mapNotNull { it.fields["default"] }
            defaults.drop(1).forEach { state.error(it.nameRange, "Only one alternative may define a default") }
            val optional = alternatives.mapIndexedNotNull { index, rule -> rule.fields["is_optional"]?.let { index to it } }
            optional.drop(1).forEach { (_, field) -> state.error(field.nameRange, "Only one alternative may define is_optional") }
            optional.firstOrNull()?.takeIf { it.first != 0 }?.let { (_, field) ->
                state.error(field.nameRange, "is_optional must be defined on the first alternative")
            }
        }
    }

    private fun validateNodeRule(
        state: AnalysisState,
        section: RuleSection,
        templates: Map<String, RuleSection>,
        indexes: Set<String>,
    ) {
        val last = section.path.lastOrNull() ?: return
        val isTemplate = section.path.firstOrNull() == "vr_template" && section.path.size == 2
        val isName = last == "vr_name"
        val type = section.fields["type"]
        val useTemplate = section.fields["use_template"]
        if (isTemplate && useTemplate != null) state.error(useTemplate.nameRange, "Templates cannot inherit from another template")
        if (type != null && useTemplate != null) state.error(useTemplate.nameRange, "A node rule cannot define both type and use_template")
        if (!isName && type == null && useTemplate == null) state.error(section.range, "Node rule requires exactly one effective type or use_template")
        if (isName && section.path.dropLast(1).lastOrNull() != "vr_any") state.error(section.range, "vr_name is only valid below a vr_any rule")
        if (type != null) {
            val id = normalize(firstValue(type.value).orEmpty())
            if (id !in TYPES) state.error(type.valueRange, "Unknown ELCL-VR type '${firstValue(type.value).orEmpty()}'")
        }
        if (useTemplate != null) {
            val target = normalize(firstValue(useTemplate.value).orEmpty())
            if (target !in templates) state.error(useTemplate.valueRange, "Unknown validation-rules template '$target'")
        }
        val effectiveType = type?.value?.let(::firstValue)?.let(::normalize)
            ?: useTemplate?.value?.let(::firstValue)?.let(::normalize)
                ?.let { templates[it]?.fields?.get("type")?.value?.let(::firstValue)?.let(::normalize) }
        if (last == "vr_entry") validateEntry(state, section, type, effectiveType)
        if (section.fields.containsKey("default") && section.fields.containsKey("is_optional")) {
            state.error(section.fields.getValue("is_optional").nameRange, "A default already makes the node optional; is_optional must not also be set")
        }
        if (section.fields.containsKey("default") && effectiveType in STRUCTURAL_TYPES) {
            state.error(section.fields.getValue("default").nameRange, "Defaults are only valid for scalar values and value lists")
        }
        if (section.fields.containsKey("is_secret") && effectiveType in STRUCTURAL_TYPES) {
            state.error(section.fields.getValue("is_secret").nameRange, "is_secret is only valid for scalar values")
        }
        section.fields["key"]?.let {
            val indexName = normalize(firstValue(it.value).orEmpty().substringBefore('['))
            if (indexName !in indexes) state.error(it.valueRange, "Unknown vr_key index reference")
        }
        if (effectiveType in LIST_TYPES) {
            val hasEntry = state.sections.any { candidate ->
                candidate.path.size > section.path.size && candidate.path.take(section.path.size) == section.path &&
                    candidate.path[section.path.size] == "vr_entry"
            }
            if (!hasEntry) state.error(section.range, "List and matrix rules require a vr_entry definition")
        }
        validateFieldsAndConstraints(state, section, effectiveType)
    }

    private fun validateEntry(state: AnalysisState, section: RuleSection, type: RuleField?, effectiveType: String?) {
        val parentType = state.sections.find { it.path == section.path.dropLast(1) }
            ?.fields?.get("type")?.value?.let(::firstValue)?.let(::normalize)
        if (parentType !in LIST_TYPES) state.error(section.range, "vr_entry requires a ValueList, ValueMatrix, or SectionList parent")
        if (section.fields.containsKey("default")) state.error(section.fields.getValue("default").nameRange, "vr_entry must not define a default")
        if (section.fields.containsKey("is_optional")) state.error(section.fields.getValue("is_optional").nameRange, "vr_entry must not be optional")
        if (parentType in VALUE_COLLECTION_TYPES && effectiveType in STRUCTURAL_TYPES + VALUE_COLLECTION_TYPES) {
            state.error(type?.valueRange ?: section.range, "Value-list and matrix entries must use scalar types")
        }
        if (parentType in SECTION_LIST_TYPES && effectiveType != null && effectiveType !in SECTION_TYPES) {
            state.error(type?.valueRange ?: section.range, "SectionList entries must use Section or SectionWithTexts")
        }
    }

    private fun validateFieldsAndConstraints(state: AnalysisState, section: RuleSection, effectiveType: String?) {
        section.fields.values.forEach { field ->
            val constraintName = field.name.removeSuffix("_error")
            val base = constraintName.removePrefix("not_")
            if (field.name !in COMMON_FIELDS && base !in CONSTRAINTS) state.error(field.nameRange, "Unknown ELCL-VR field '${field.name}'")
            if (field.name.endsWith("_error") && !section.fields.containsKey(constraintName)) {
                state.error(field.nameRange, "Custom error field requires its exact matching '$constraintName' constraint")
            }
            if (field.name.endsWith("_error") && base in VERSION_CONSTRAINTS) {
                state.error(field.nameRange, "Version constraints do not support custom error messages")
            }
            if (field.name.startsWith("not_") && base !in NEGATABLE) state.error(field.nameRange, "Constraint '$base' has no not_ form")
            if (constraintName.startsWith("not_") && section.fields.containsKey(base)) {
                state.error(field.nameRange, "A constraint cannot combine '$base' with its negated form")
            }
            if (base in TEXT_CONSTRAINTS && effectiveType != "text") state.error(field.nameRange, "Constraint '$base' is only applicable to type Text")
            if (base == "multiple" && effectiveType !in MULTIPLE_TYPES) state.error(field.nameRange, "multiple is not applicable to this node type")
            if (base == "in" && effectiveType !in IN_TYPES) state.error(field.nameRange, "in is not applicable to this node type")
            if (base == "key" && effectiveType !in setOf("integer", "text")) state.error(field.nameRange, "key is only applicable to Integer and Text nodes")
        }
        section.fields["in"]?.let { field ->
            val values = valueList(field.value).map { if (effectiveType == "text") it.lowercase() else it }
            if (values.size != values.distinct().size) state.error(field.valueRange, "The in constraint must not contain duplicate values")
        }
        section.fields["version"]?.let { field ->
            val versions = valueList(field.value)
            if (versions.any { it.toLongOrNull() == null }) state.error(field.valueRange, "version accepts only integer values")
            if (versions.size != versions.distinct().size) state.error(field.valueRange, "version must not contain duplicate values")
        }
        listOf("minimum_version", "maximum_version").forEach { name ->
            section.fields[name]?.let {
                if (valueList(it.value).size != 1 || firstValue(it.value)?.toLongOrNull() == null) {
                    state.error(it.valueRange, "$name requires exactly one integer")
                }
            }
        }
        val minimum = section.fields["minimum"]?.value?.let(::firstNumber)
        val maximum = section.fields["maximum"]?.value?.let(::firstNumber)
        if (minimum != null && maximum != null && minimum > maximum) {
            state.error(section.fields.getValue("maximum").valueRange, "maximum must not be smaller than minimum")
        }
        val minVersion = section.fields["minimum_version"]?.value?.let(::firstValue)
        val maxVersion = section.fields["maximum_version"]?.value?.let(::firstValue)
        if (minVersion != null && maxVersion != null && compareVersions(minVersion, maxVersion) > 0) {
            state.error(section.fields.getValue("maximum_version").valueRange, "maximum_version must not be smaller than minimum_version")
        }
    }

    private fun validateDependency(state: AnalysisState, section: RuleSection) {
        if (!section.list) state.error(section.range, "vr_dependency must be a section list (*[...]* syntax)")
        listOf("mode", "source", "target").forEach { name ->
            if (name !in section.fields) state.error(section.range, "vr_dependency requires '$name'")
        }
        section.fields["mode"]?.let {
            if (normalize(firstValue(it.value).orEmpty()) !in MODES) state.error(it.valueRange, "Unknown dependency mode; expected if, if_not, or, xor, xnor, or and")
        }
        section.fields.keys.filter { it !in setOf("mode", "source", "target", "error") }.forEach {
            state.error(section.fields.getValue(it).nameRange, "Field '$it' is not valid in vr_dependency")
        }
        listOf("source", "target").forEach { fieldName ->
            section.fields[fieldName]?.let { field ->
                valueList(field.value).forEach { reference -> validateDependencyReference(state, section, field, reference) }
            }
        }
    }

    private fun validateDependencyReference(state: AnalysisState, dependency: RuleSection, field: RuleField, reference: String) {
        val relative = reference.split('.').map(::normalize)
        if (relative.any { it == "vr_entry" }) {
            state.error(field.valueRange, "Dependencies cannot reference values inside section-list entries")
            return
        }
        val path = dependency.path.dropLast(1) + relative
        val rules = state.sections.filter { it.path == path }
        if (rules.isEmpty()) {
            state.error(field.valueRange, "Dependency reference '$reference' has no node-rule definition in this scope")
            return
        }
        if (rules.size > 1 || rules.any { it.list }) state.error(field.valueRange, "Dependencies cannot reference nodes defined by alternatives")
        val conditionallyPresent = path.indices.any { index ->
            state.sections.filter { it.path == path.take(index + 1) }.any { "is_optional" in it.fields || "default" in it.fields }
        }
        if (!conditionallyPresent) state.error(field.valueRange, "Dependency reference '$reference' is unconditionally required")
    }

    private fun validateIndex(state: AnalysisState, section: RuleSection) {
        if (!section.list) state.error(section.range, "vr_key must be a section list (*[...]* syntax)")
        if ("key" !in section.fields) state.error(section.range, "vr_key requires 'key'")
        section.fields.keys.filter { it !in setOf("name", "key", "case_sensitive") }.forEach {
            state.error(section.fields.getValue(it).nameRange, "Field '$it' is not valid in vr_key")
        }
        section.fields["key"]?.let { field ->
            val listRoots = mutableSetOf<List<String>>()
            valueList(field.value).forEach { reference ->
                val path = reference.split('.').map(::normalize)
                val entryIndexes = path.indices.filter { path[it] == "vr_entry" }
                if (entryIndexes.size != 1) {
                    state.error(field.valueRange, "Index key paths must pass through exactly one SectionList vr_entry")
                    return@forEach
                }
                val listPath = path.take(entryIndexes.single())
                listRoots += listPath
                val listType = state.sections.find { it.path == listPath }?.fields?.get("type")?.value?.let(::firstValue)?.let(::normalize)
                if (listType !in SECTION_LIST_TYPES) state.error(field.valueRange, "Index key path must reference a SectionList")
                val targetType = state.sections.find { it.path == path }?.fields?.get("type")?.value?.let(::firstValue)?.let(::normalize)
                if (targetType !in setOf("integer", "text")) state.error(field.valueRange, "Indexed values must use type Integer or Text")
            }
            if (listRoots.size > 1) state.error(field.valueRange, "All values in a composite index must reference the same SectionList")
        }
    }

    private companion object {
        val VALUE_COLLECTION_TYPES = setOf("value_list", "valuelist", "value_matrix", "valuematrix")
        val SECTION_LIST_TYPES = setOf("section_list", "sectionlist")
        val SECTION_TYPES = setOf("section", "section_with_texts", "sectionwithtexts")
        val LIST_TYPES = VALUE_COLLECTION_TYPES + SECTION_LIST_TYPES
        val VERSION_CONSTRAINTS = setOf("version", "minimum_version", "maximum_version")
        val TEXT_CONSTRAINTS = setOf("matches", "starts", "ends", "contains", "allowed_chars", "chars")
    }
}
