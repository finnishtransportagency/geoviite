package fi.fta.geoviite.infra.logging

import org.apache.logging.log4j.ThreadContext
import java.util.UUID

const val SPAN_IDS_KEY = "spanIds"
const val SPAN_ID_SEPARATOR = "-"

fun <T> withLogSpan(spanId: String = newSpanId(), op: () -> T): T {
    val previousSpans = ThreadContext.get(SPAN_IDS_KEY)

    return try {
        ThreadContext.put(SPAN_IDS_KEY, pushSpan(previousSpans, spanId))
        op()
    } finally {
        if (previousSpans?.isNotEmpty() == true) {
            ThreadContext.put(SPAN_IDS_KEY, previousSpans)
        } else {
            ThreadContext.remove(SPAN_IDS_KEY)
        }
    }
}

private fun newSpanId(): String {
    return UUID.randomUUID().toString().take(8)
}

private fun pushSpan(spans: String?, spanId: String): String {
    val spanAsList = spans?.split(SPAN_ID_SEPARATOR) ?: emptyList()
    return (spanAsList + spanId).joinToString(SPAN_ID_SEPARATOR)
}
