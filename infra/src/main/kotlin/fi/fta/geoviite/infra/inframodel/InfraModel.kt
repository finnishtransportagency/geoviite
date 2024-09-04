package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.error.InframodelParsingException

interface InfraModel {
    val language: String?
    val featureDictionary: InfraModelFeatureDictionary?
    val units: InfraModelUnits?
    val coordinateSystem: InfraModelCoordinateSystem?
    val project: InfraModelProject?
    val application: InfraModelApplication?
    val alignmentGroups: List<InfraModelAlignmentGroup>
}

interface InfraModelFeatureDictionary {
    val name: String
    val version: String?
}

interface InfraModelUnits {
    val metric: InfraModelMetric?
}

interface InfraModelMetric {
    val linearUnit: String
    val areaUnit: String
    val volumeUnit: String
    val angularUnit: String
    val directionUnit: String
}

interface InfraModelProject {
    val name: String
    val desc: String?
    val feature: InfraModelFeature?
}

interface InfraModelApplication {
    val name: String
    val desc: String?
    val manufacturer: String
    val manufacturerURL: String
    val version: String
    val author: InfraModelAuthor?
}

interface InfraModelAuthor {
    val createdBy: String?
    val createdByEmail: String?
    val company: String?
    val companyUR: String?
    val timeStamp: String?
}

interface InfraModelCoordinateSystem {
    val name: String
    val epsgCode: String
    val verticalCoordinateSystemName: String
    val rotationAngle: String
}

interface InfraModelAlignmentGroup {
    val name: String
    val desc: String?
    val state: String?
    val alignments: List<InfraModelAlignment>
}

interface InfraModelAlignment {
    val name: String
    val desc: String?
    val state: String?
    val oid: String?
    val staStart: String
    val elements: List<InfraModelGeometryElement>
    val cant: InfraModelCant?
    val profile: InfraModelProfile?
    val features: List<InfraModelFeature>
    val staEquations: List<InfraModelStaEquation>
}

interface InfraModelStaEquation {
    val staBack: String
    val staAhead: String
    val staInternal: String
    val desc: String
    val feature: InfraModelFeature?
}

interface InfraModelGeometryElement {
    val name: String?
    val oID: String?
    val staStart: String
    val length: String
    val start: String
    val end: String
    val features: List<InfraModelFeature>
}

interface InfraModelLine : InfraModelGeometryElement

interface InfraModelCurve : InfraModelGeometryElement {
    val rot: String
    val radius: String
    val chord: String
    val center: String
}

interface InfraModelSpiral : InfraModelGeometryElement {
    val rot: String
    val constant: String?
    val dirStart: String?
    val dirEnd: String?
    val radiusStart: String?
    val radiusEnd: String?
    val spiType: String
    val pi: String
}

interface InfraModelCant {
    val name: String
    val desc: String?
    val gauge: String
    val rotationPoint: String
    val stations: List<InfraModelCantStation>
}

interface InfraModelCantStation {
    val station: String
    val appliedCant: String
    val curvature: String
    val transitionType: String?
}

interface InfraModelProfile {
    val profAlign: InfraModelProfAlign?
    val features: List<InfraModelFeature>
}

interface InfraModelProfAlign {
    val name: String
    val elements: List<InfraModelProfileElement>
    val features: List<InfraModelFeature>
}

interface InfraModelProfileElement {
    val desc: String?
    val point: String
}

interface InfraModelPvi {
    val desc: String?
    val point: String
}

interface InfraModelCircCurve {
    val length: String
    val radius: String
}

interface InfraModelFeature {
    val code: String
    val properties: List<InfraModelProperty>

    fun getProperty(label: String): String {
        return properties.find { p -> p.label == label }?.value
            ?: throw InframodelParsingException("Feature $code doesn't have property $label")
    }

    fun getPropertyAnyMatch(labels: List<String>): String? {
        return properties.find { p -> labels.contains(p.label) }?.value
    }
}

interface InfraModelProperty {
    val label: String
    val value: String
}
