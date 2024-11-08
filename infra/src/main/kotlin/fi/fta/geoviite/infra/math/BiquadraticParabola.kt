package fi.fta.geoviite.infra.math

import kotlin.math.pow

/**
 * Sources for these functions:
 *
 * https://julkaisut.vayla.fi/pdf3/lts_2011-22_raidegeometrian_suunnittelu_web.pdf
 * https://standards.buildingsmart.org/IFC/DEV/IFC4_2/FINAL/HTML/schema/ifcgeometryresource/lexical/ifctransitioncurvetype.htm
 * https://www.researchgate.net/publication/289952758_The_fourth_degree_parabola_Bi-quadratic_parabola_as_a_transition_curve
 */
fun biquadraticParabolaPointAtOffset(offset: Double, R: Double, L: Double): Point {
    val y = if (offset <= L / 2.0) biquadraticY1(offset, R, L) else biquadraticY2(offset, R, L)
    return Point(offset, y)
}

private fun biquadraticY1(l: Double, R: Double, L: Double): Double {
    return l.pow(4) / (6 * R * L.pow(2))
}

private fun biquadraticY2(l: Double, R: Double, L: Double): Double {
    return -l.pow(4) / (6 * R * L.pow(2)) + 2 * l.pow(3) / (3 * R * L) - l.pow(2) / (2 * R) + L * l / (6 * R) -
        L.pow(2) / (48 * R)
}

fun biquadraticSTransition(offset: Double, D: Double, L: Double): Double {
    return if (offset <= L / 2.0) biquadraticSTransition1(offset, D, L) else biquadraticSTransition2(offset, D, L)
}

private fun biquadraticSTransition1(offset: Double, D: Double, L: Double): Double {
    return 2 * D * offset.pow(2) / L.pow(2)
}

private fun biquadraticSTransition2(offset: Double, D: Double, L: Double): Double {
    return D - (2 * D * (L - offset).pow(2) / L.pow(2))
}
