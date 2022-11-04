package fi.fta.geoviite.infra.ui.util

import fi.fta.geoviite.infra.ui.pagemodel.common.PageModel
import org.json.JSONObject
import org.openqa.selenium.logging.LogEntries
import org.openqa.selenium.logging.LogEntry
import org.openqa.selenium.logging.LogType
import java.util.*
import java.util.logging.Level

class BrowserLogUtil {

    companion object {
        fun printLogEntries(logEntries: LogEntries) {
            printLogEntries(logEntries.toList(), "LOG ENTRIES")
        }

        fun printLogEntries(logEntries: List<LogEntry>, header: String) {
            println("============ " + header + " ============")
            for (entry in logEntries) {
                println(Date(entry.timestamp).toString() + " " + entry.level + " " + entry.message)
            }
            println("============ " + header + " ============")
        }

        fun filter(logEntries: LogEntries, start: Long, end: Long): List<LogEntry> {
            return logEntries.filter { logEntry -> logEntry.timestamp > start && logEntry.timestamp < end }
        }

        fun filter(logEntries: LogEntries, start: Long): List<LogEntry> {
            return logEntries.filter { logEntry -> logEntry.timestamp > start }
        }

        fun filter(logEntries: List<LogEntry>, regex: Regex): List<LogEntry> {
            return logEntries.filter { logEntry -> logEntry.message.matches(regex) }
        }

        fun hasEntryLeve(logEntries: List<LogEntry>, level: Level): Boolean {
            val filtered = logEntries.filter { logEntry -> logEntry.level.equals(level) }
            return filtered.size > 0;
        }

        fun messageMatches(logEntries: List<LogEntry>, regex: Regex): Boolean {
            val filtered = logEntries.filter { logEntry -> logEntry.message.matches(regex) }
            return filtered.size > 0
        }

        protected fun printBrowserLogs() {
            val logEntries: LogEntries = PageModel.browser().manage().logs().get(LogType.BROWSER)
            BrowserLogUtil.printLogEntries(logEntries)
        }

        protected fun printNetworkLogsAll() {
            val logEntries = PageModel.browser().manage().logs().get(LogType.PERFORMANCE)
            BrowserLogUtil.printLogEntries(logEntries.toList(), "ALL NETWORK LOGS")
        }

        protected fun printNetworkLogsResponses() {
            val logEntries: LogEntries = PageModel.browser().manage().logs().get(LogType.PERFORMANCE)
            val filtered = BrowserLogUtil.filter(logEntries.toList(), ".*\"Network.responseReceived\".*".toRegex())
            for (entry in filtered) {
                val jsonObject = JSONObject(entry.message)

                val response = jsonObject.getJSONObject("message").getJSONObject("params").getJSONObject("response")
                val url = response.get("url")
                val statusCode = response.get("status")
                val statusText = response.get("statusText")

                if (!url.toString().contentEquals("data:,")) {
                    println("$url $statusCode/$statusText")
                }

            }
            //BrowserLogUtil.printLogEntries(filtered, "RESPONSE NETWORK LOGS")
        }
    }

}
