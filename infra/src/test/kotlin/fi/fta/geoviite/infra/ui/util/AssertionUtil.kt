package fi.fta.geoviite.infra.ui.util

fun assertStringContains(expectedList: List<String>, actual: String) =
    expectedList.forEach{ expected -> kotlin.test.assertContains(actual, expected) }