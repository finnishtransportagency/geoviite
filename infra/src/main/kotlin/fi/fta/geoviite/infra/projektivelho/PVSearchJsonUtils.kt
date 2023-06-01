package fi.fta.geoviite.infra.projektivelho

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory

enum class PVApiSearchType(val apiValue: String) {
    TARGET_SEARCH("kohdeluokkahaku"),
    ROAD_SEARCH("tieosuushaku")
}

enum class PVApiOrderingDirection(val apiValue: String) {
    ASCENDING("nouseva"),
    DESCENDING("laskeva")
}

const val SETTINGS = "asetukset"
const val FORMULA = "lauseke"
const val TARGET_CATEGORIES = "kohdeluokat"
const val SEARCH_TYPE = "tyyppi"
const val RESULT_LIMIT = "koko"
const val ORDER_BY = "jarjesta"

// Not exhaustive. Add more operators if needed
enum class PVApiSearchOperator(val apiValue: String) {
    AND("ja"),
    OR("tai"),
    EQUALS("yhtasuuri"),
    GREATER_THAN("suurempi-kuin"),
    CONTAINS_TEXT("sisaltaa-tekstin"),
    IN("joukossa")
}

fun searchRoot(settings: JsonNode, formula: JsonNode, targetCategories: JsonNode) =
    JsonNodeFactory.instance.objectNode().let { rootNode ->
        rootNode.set<JsonNode>(SETTINGS, settings)
        rootNode.set<JsonNode>(FORMULA, formula)
        rootNode.set<JsonNode>(TARGET_CATEGORIES, targetCategories)
        rootNode
    }

fun settings(searchType: PVApiSearchType, maxResultCount: Int, ordering: JsonNode) =
    JsonNodeFactory.instance.objectNode().let { settingsNode ->
        settingsNode.put(SEARCH_TYPE, searchType.apiValue)
        settingsNode.put(RESULT_LIMIT, maxResultCount)
        settingsNode.set<JsonNode>(ORDER_BY, ordering)
        settingsNode
    }

fun multiArgumentOperation(operator: PVApiSearchOperator, vararg children: JsonNode) =
    JsonNodeFactory.instance.arrayNode().let { arrayNode ->
        arrayNode.add(operator.apiValue)
        children.forEach { child -> arrayNode.add(child) }
        arrayNode
    }

fun and(vararg children: JsonNode) =
    multiArgumentOperation(PVApiSearchOperator.AND, *children)

fun or(vararg children: JsonNode) =
    multiArgumentOperation(PVApiSearchOperator.OR, *children)

fun equals(path: List<String>, value: String) =
    operationWithPath(PVApiSearchOperator.EQUALS, path, value)

fun greaterThan(path: List<String>, value: String) =
    operationWithPath(PVApiSearchOperator.GREATER_THAN, path, value)

fun includesText(path: List<String>, value: String) =
    operationWithPath(PVApiSearchOperator.CONTAINS_TEXT, path, value)

fun contains(path: List<String>, values: List<String>) =
    operationWithPath(PVApiSearchOperator.IN, path, values)

fun operationWithPath(operator: PVApiSearchOperator, path: List<String>, value: String) =
    JsonNodeFactory.instance.arrayNode().let { arrayNode ->
        arrayNode.add(operator.apiValue)
        arrayNode.add(jsonStringArray(path))
        arrayNode.add(value)
        arrayNode
    }

fun operationWithPath(operator: PVApiSearchOperator, path: List<String>, values: List<String>) =
    JsonNodeFactory.instance.arrayNode().let { arrayNode ->
        arrayNode.add(operator.apiValue)
        arrayNode.add(jsonStringArray(path))
        arrayNode.add(jsonStringArray(values))
        arrayNode
    }

fun ordering(path: List<String>, direction: PVApiOrderingDirection) =
    JsonNodeFactory.instance.arrayNode().let { orderNode ->
        orderNode.add(jsonStringArray(path))
        orderNode.add(direction.apiValue)
        orderNode
    }

fun jsonObjectArray(items: List<JsonNode>) =
    JsonNodeFactory.instance.arrayNode().let { array ->
        items.forEach { item ->
            array.add(item)
        }
        array
    }

fun jsonStringArray(items: List<String>) =
    JsonNodeFactory.instance.arrayNode().let { array ->
        items.forEach { item ->
            array.add(item)
        }
        array
    }
