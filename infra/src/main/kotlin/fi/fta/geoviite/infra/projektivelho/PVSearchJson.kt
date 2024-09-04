package fi.fta.geoviite.infra.projektivelho

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.fta.geoviite.infra.common.Oid
import java.time.Instant

enum class PVApiSearchType(val apiValue: String) {
    TARGET_SEARCH("kohdeluokkahaku"),
    ROAD_SEARCH("tieosuushaku"),
}

enum class PVApiOrderingDirection(val apiValue: String) {
    ASCENDING("nouseva"),
    DESCENDING("laskeva"),
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
    IN("joukossa"),
}

fun searchRoot(settings: JsonNode, formula: JsonNode, targetCategories: JsonNode): ObjectNode =
    JsonNodeFactory.instance.objectNode().also { rootNode ->
        rootNode.set<JsonNode>(SETTINGS, settings)
        rootNode.set<JsonNode>(FORMULA, formula)
        rootNode.set<JsonNode>(TARGET_CATEGORIES, targetCategories)
    }

fun settings(searchType: PVApiSearchType, maxResultCount: Int, ordering: JsonNode): ObjectNode =
    JsonNodeFactory.instance.objectNode().also { settingsNode ->
        settingsNode.put(SEARCH_TYPE, searchType.apiValue)
        settingsNode.put(RESULT_LIMIT, maxResultCount)
        settingsNode.set<JsonNode>(ORDER_BY, ordering)
    }

fun multiArgumentOperation(operator: PVApiSearchOperator, vararg children: JsonNode): ArrayNode =
    JsonNodeFactory.instance.arrayNode().also { arrayNode ->
        arrayNode.add(operator.apiValue)
        children.forEach(arrayNode::add)
    }

fun and(vararg children: JsonNode) = multiArgumentOperation(PVApiSearchOperator.AND, *children)

fun or(vararg children: JsonNode) = multiArgumentOperation(PVApiSearchOperator.OR, *children)

fun equals(path: List<String>, value: String) = operationWithPath(PVApiSearchOperator.EQUALS, path, value)

fun greaterThan(path: List<String>, value: String) = operationWithPath(PVApiSearchOperator.GREATER_THAN, path, value)

fun includesText(path: List<String>, value: String) = operationWithPath(PVApiSearchOperator.CONTAINS_TEXT, path, value)

fun contains(path: List<String>, values: List<String>) = operationWithPath(PVApiSearchOperator.IN, path, values)

fun operationWithPath(operator: PVApiSearchOperator, path: List<String>, value: String): ArrayNode =
    JsonNodeFactory.instance.arrayNode().also { arrayNode ->
        arrayNode.add(operator.apiValue)
        arrayNode.add(jsonStringArray(path))
        arrayNode.add(value)
    }

fun operationWithPath(operator: PVApiSearchOperator, path: List<String>, values: List<String>): ArrayNode =
    JsonNodeFactory.instance.arrayNode().also { arrayNode ->
        arrayNode.add(operator.apiValue)
        arrayNode.add(jsonStringArray(path))
        arrayNode.add(jsonStringArray(values))
    }

fun ordering(path: List<String>, direction: PVApiOrderingDirection): ArrayNode =
    JsonNodeFactory.instance.arrayNode().also { orderNode ->
        orderNode.add(jsonStringArray(path))
        orderNode.add(direction.apiValue)
    }

fun jsonObjectArray(items: List<JsonNode>): ArrayNode =
    JsonNodeFactory.instance.arrayNode(items.size).also { array -> items.forEach(array::add) }

fun jsonStringArray(items: List<String>): ArrayNode =
    JsonNodeFactory.instance.arrayNode(items.size).also { array -> items.forEach(array::add) }

fun searchJson(cutoffDate: Instant, minOid: Oid<PVDocument>?, maxResultCount: Int) =
    searchRoot(
        settings =
            settings(
                searchType = PVApiSearchType.TARGET_SEARCH,
                maxResultCount = maxResultCount,
                ordering =
                    jsonObjectArray(
                        listOf(
                            ordering(
                                listOf("aineisto/aineisto", "tuorein-versio", "muokattu"),
                                PVApiOrderingDirection.ASCENDING,
                            ),
                            ordering(listOf("aineisto/aineisto", "oid"), PVApiOrderingDirection.ASCENDING),
                        )
                    ),
            ),
        formula =
            and(
                or(
                    greaterThan(
                        path = listOf("aineisto/aineisto", "tuorein-versio", "muokattu"),
                        value = cutoffDate.toString(),
                    ),
                    and(
                        equals(
                            path = listOf("aineisto/aineisto", "tuorein-versio", "muokattu"),
                            value = cutoffDate.toString(),
                        ),
                        greaterThan(
                            path = listOf("aineisto/aineisto", "oid"),
                            value = minOid?.let { minOid.toString() } ?: "",
                        ),
                    ),
                ),
                includesText(path = listOf("aineisto/aineisto", "tuorein-versio", "nimi"), value = ".xml"),
                contains(
                    path = listOf("aineisto/aineisto", "metatiedot", "tekniikka-alat"),
                    values = listOf("tekniikka-ala/ta15"),
                ),
                or(equals(path = listOf("aineisto/aineisto", "metatiedot", "ryhma"), value = "aineistoryhma/ar07")),
            ),
        targetCategories = JsonNodeFactory.instance.arrayNode().add("aineisto/aineisto"),
    )
