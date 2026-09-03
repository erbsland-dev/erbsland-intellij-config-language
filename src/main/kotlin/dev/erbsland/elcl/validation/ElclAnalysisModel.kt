package dev.erbsland.elcl.validation

import com.intellij.openapi.util.TextRange

/** Parsed section header used while building the recoverable document model. */
internal data class ParsedSection(val path: String, val list: Boolean, val range: TextRange)

/** Parsed name/value assignment with precise diagnostic ranges. */
internal data class Assignment(
    val name: String,
    val value: String,
    val nameRange: TextRange,
    val valueRange: TextRange,
)

/** Supported multiline value families with distinct content rules. */
internal enum class MultilineKind { TEXT, CODE, REGEX, BYTES }

/** Delimiter and content family recognized at a multiline opener. */
internal data class MultilineStart(val kind: MultilineKind, val close: String)

/** Indentation contract for an active multiline value. */
internal data class Multiline(var indent: String?, val start: MultilineStart) {
    val close: String get() = start.close
}

/** One normalized field in an ELCL-VR section. */
internal data class RuleField(
    val name: String,
    val value: String,
    val nameRange: TextRange,
    val valueRange: TextRange,
)

/** One ELCL-VR section and the fields declared directly below it. */
internal data class RuleSection(
    val path: List<String>,
    val list: Boolean,
    val range: TextRange,
    val source: String,
    val fields: MutableMap<String, RuleField> = linkedMapOf(),
)

/** Mutable result shared by document parsing and optional VR validation. */
internal class AnalysisState(val text: String) {
    val diagnostics = mutableListOf<ElclDiagnostic>()
    val sections = mutableListOf<RuleSection>()
    val metaValues = mutableSetOf<String>()
    val namespace = NamespaceTree()

    fun error(range: TextRange, message: String) {
        diagnostics += ElclDiagnostic(range, message)
    }

    fun warning(range: TextRange, message: String) {
        diagnostics += ElclDiagnostic(range, message, ElclDiagnosticSeverity.WARNING)
    }
}

/** Value-free namespace tree applying ELCL name-conflict rules per scope. */
internal class NamespaceTree {
    private val root = NamespaceScope()

    /** Opens a map section or a fresh list entry, returning null on conflict. */
    fun openSection(path: List<String>, list: Boolean): NamespaceScope? {
        if (path.isEmpty()) return null
        var scope = root
        path.dropLast(1).forEach { name ->
            scope = when (val existing = scope.children[name]) {
                null -> NamespaceSection(explicit = false).also { scope.children[name] = it }.scope
                is NamespaceSection -> existing.scope
                is NamespaceList -> existing.entries.lastOrNull() ?: return null
                NamespaceValue -> return null
            }
        }

        val name = path.last()
        val existing = scope.children[name]
        if (list) {
            val sectionList = when (existing) {
                null -> NamespaceList().also { scope.children[name] = it }
                is NamespaceList -> existing
                else -> return null
            }
            return NamespaceScope().also(sectionList.entries::add)
        }

        return when (existing) {
            null -> NamespaceSection(explicit = true).also { scope.children[name] = it }.scope
            is NamespaceSection -> if (existing.explicit) null else {
                existing.explicit = true
                existing.scope
            }
            else -> null
        }
    }

    /** Merges an included document and returns every conflicting normalized path. */
    fun mergeFrom(other: NamespaceTree): Set<String> = linkedSetOf<String>().also { conflicts ->
        mergeScopes(root, other.root, emptyList(), conflicts)
    }

    private fun mergeScopes(
        destination: NamespaceScope,
        source: NamespaceScope,
        parentPath: List<String>,
        conflicts: MutableSet<String>,
    ) {
        source.children.forEach { (name, sourceNode) ->
            val path = parentPath + name
            val destinationNode = destination.children[name]
            if (destinationNode == null) {
                destination.children[name] = sourceNode.copyNode()
                return@forEach
            }
            when {
                destinationNode is NamespaceSection && sourceNode is NamespaceSection -> {
                    if (destinationNode.explicit && sourceNode.explicit) conflicts += path.joinToString(".")
                    destinationNode.explicit = destinationNode.explicit || sourceNode.explicit
                    mergeScopes(destinationNode.scope, sourceNode.scope, path, conflicts)
                }
                destinationNode is NamespaceList && sourceNode is NamespaceList -> {
                    destinationNode.entries += sourceNode.entries.map(NamespaceScope::copyScope)
                }
                destinationNode is NamespaceList && sourceNode is NamespaceSection && !sourceNode.explicit -> {
                    val lastEntry = destinationNode.entries.lastOrNull()
                    if (lastEntry == null) conflicts += path.joinToString(".")
                    else mergeScopes(lastEntry, sourceNode.scope, path, conflicts)
                }
                else -> conflicts += path.joinToString(".")
            }
        }
    }
}

/** Namespace members directly declared in one section or list entry. */
internal class NamespaceScope {
    val children = linkedMapOf<String, NamespaceNode>()
    fun defineValue(name: String): Boolean = children.putIfAbsent(name, NamespaceValue) == null
    fun copyScope(): NamespaceScope = NamespaceScope().also { copy ->
        children.forEach { (name, node) -> copy.children[name] = node.copyNode() }
    }
}

/** Node kind stored in the conflict-only namespace tree. */
internal sealed interface NamespaceNode {
    fun copyNode(): NamespaceNode
}

/** Map section, including implicit parents synthesized from longer paths. */
internal class NamespaceSection(var explicit: Boolean, val scope: NamespaceScope = NamespaceScope()) : NamespaceNode {
    override fun copyNode(): NamespaceNode = NamespaceSection(explicit, scope.copyScope())
}

/** Section list whose entries intentionally keep independent value scopes. */
internal class NamespaceList(val entries: MutableList<NamespaceScope> = mutableListOf()) : NamespaceNode {
    override fun copyNode(): NamespaceNode = NamespaceList(entries.map(NamespaceScope::copyScope).toMutableList())
}

/** Leaf marker reserving a normalized name for a value. */
internal data object NamespaceValue : NamespaceNode {
    override fun copyNode(): NamespaceNode = this
}
