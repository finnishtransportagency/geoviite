package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.error.InframodelParsingException
import jakarta.xml.bind.annotation.*

const val XMLNS_404 = "http://buildingsmart.fi/inframodel/404"

@XmlRootElement(name = "LandXML", namespace = XMLNS_404)
@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModel404(
    @field:XmlAttribute override val language: String? = null,

    @field:XmlElement(name = "FeatureDictionary", namespace = XMLNS_404)
    override val featureDictionary: InfraModelFeatureDictionary404? = null,
    @field:XmlElement(name = "Units", namespace = XMLNS_404)
    override val units: InfraModelUnits404? = null,
    @field:XmlElement(name = "CoordinateSystem", namespace = XMLNS_404)
    override val coordinateSystem: InfraModelCoordinateSystem404? = null,
    @field:XmlElement(name = "Project", namespace = XMLNS_404)
    override val project: InfraModelProject404? = null,
    @field:XmlElement(name = "Application", namespace = XMLNS_404)
    override val application: InfraModelApplication404? = null,
    @field:XmlElement(name = "Alignments", namespace = XMLNS_404)
    override val alignmentGroups: List<InfraModelAlignmentGroup404> = arrayListOf(),
): InfraModel

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelFeatureDictionary404(
    @field:XmlAttribute override val name: String = "",
    @field:XmlAttribute override val version: String? = null
):InfraModelFeatureDictionary

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelUnits404(
    @field:XmlElement(name = "Metric", namespace = XMLNS_404)
    override val metric: InfraModelMetric404? = null,
):InfraModelUnits

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelMetric404(
    @field:XmlAttribute override val linearUnit: String = "",
    @field:XmlAttribute override val areaUnit: String = "",
    @field:XmlAttribute override val volumeUnit: String = "",
    @field:XmlAttribute override val angularUnit: String = "",
    @field:XmlAttribute override val directionUnit: String = "",
):InfraModelMetric

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelProject404(
    @field:XmlAttribute override val name: String = "",
    @field:XmlAttribute override val desc: String? = null,
    @field:XmlElement(name = "Feature", namespace = XMLNS_404)
    override val feature: InfraModelFeature404? = null,
):InfraModelProject

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelApplication404(
    @field:XmlAttribute override val name: String = "",
    @field:XmlAttribute override val desc: String? = null,
    @field:XmlAttribute override val manufacturer: String = "",
    @field:XmlAttribute override val manufacturerURL: String = "",
    @field:XmlAttribute override val version: String = "",
    @field:XmlElement(name = "Author", namespace = XMLNS_404)
    override val author: InfraModelAuthor404? = null,
):InfraModelApplication

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelAuthor404(
    @field:XmlAttribute override val createdBy: String? = null,
    @field:XmlAttribute override val createdByEmail: String? = null,
    @field:XmlAttribute override val company: String? = null,
    @field:XmlAttribute override val companyUR: String? = null,
    @field:XmlAttribute override val timeStamp: String? = null,
):InfraModelAuthor

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelCoordinateSystem404(
    @field:XmlAttribute override val name: String = "",
    @field:XmlAttribute override val epsgCode: String = "",
    @field:XmlAttribute override val verticalCoordinateSystemName: String = "",
    @field:XmlAttribute override val rotationAngle: String = "",
):InfraModelCoordinateSystem

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelAlignmentGroup404(
    @field:XmlAttribute override val name: String = "",
    @field:XmlAttribute override val desc: String? = null,
    @field:XmlAttribute override val state: String? = null,
    @field:XmlElement(name = "Alignment", namespace = XMLNS_404)
    override val alignments: List<InfraModelAlignment404> = arrayListOf(),
):InfraModelAlignmentGroup

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelAlignment404(
    @field:XmlAttribute override val name: String = "",
    @field:XmlAttribute override val desc: String? = null,
    @field:XmlAttribute override val state: String? = null,
    @field:XmlAttribute override val oid: String? = null,
    @field:XmlAttribute override val staStart: String = "",

    @field:XmlElementWrapper(name = "CoordGeom", namespace = XMLNS_404)
    @field:XmlElements(
        XmlElement(name = "Line", namespace = XMLNS_404, type = InfraModelLine404::class),
        XmlElement(name = "Curve", namespace = XMLNS_404, type = InfraModelCurve404::class),
        XmlElement(name = "Spiral", namespace = XMLNS_404, type = InfraModelSpiral404::class)
    )
    override val elements: List<InfraModelGeometryElement> = arrayListOf(),

    @field:XmlElement(name = "Cant", namespace = XMLNS_404)
    override val cant: InfraModelCant404? = null,

    @field:XmlElement(name = "Profile", namespace = XMLNS_404)
    override val profile: InfraModelProfile404? = null,

    @field:XmlElement(name = "Feature", namespace = XMLNS_404)
    override val features: List<InfraModelFeature404> = arrayListOf(),

    @field:XmlElement(name = "StaEquation", namespace = XMLNS_404)
    override val staEquations: List<InfraModelStaEquation404> = arrayListOf(),
):InfraModelAlignment

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelStaEquation404(
    @field:XmlAttribute override val staBack: String = "",
    @field:XmlAttribute override val staAhead: String = "",
    @field:XmlAttribute override val staInternal: String = "",
    @field:XmlAttribute override val desc: String = "",
    @field:XmlElement(name = "Feature", namespace = XMLNS_404)
    override val feature: InfraModelFeature404? = null,
):InfraModelStaEquation

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelLine404(
    @field:XmlAttribute override val name: String? = null,
    @field:XmlAttribute override val staStart: String = "",
    @field:XmlAttribute override val length: String = "",
    @field:XmlAttribute override val oID: String? = null,

    @field:XmlElement(name = "Start", namespace = XMLNS_404)
    override val start: String = "",
    @field:XmlElement(name = "End", namespace = XMLNS_404)
    override val end: String = "",
    @field:XmlElement(name = "Feature", namespace = XMLNS_404)
    override val features: List<InfraModelFeature404> = arrayListOf(),
) : InfraModelLine

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelCurve404(
    @field:XmlAttribute override val name: String? = null,
    @field:XmlAttribute override val staStart: String = "",
    @field:XmlAttribute override val length: String = "",
    @field:XmlAttribute override val oID: String? = null,
    @field:XmlAttribute override val rot: String = "",
    @field:XmlAttribute override val radius: String = "",
    @field:XmlAttribute override val chord: String = "",

    @field:XmlElement(name = "Start", namespace = XMLNS_404)
    override val start: String = "",
    @field:XmlElement(name = "Center", namespace = XMLNS_404)
    override val center: String = "",
    @field:XmlElement(name = "End", namespace = XMLNS_404)
    override val end: String = "",
    @field:XmlElement(name = "Feature", namespace = XMLNS_404)
    override val features: List<InfraModelFeature404> = arrayListOf(),
): InfraModelCurve

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelSpiral404(
    @field:XmlAttribute override val name: String? = null,
    @field:XmlAttribute override val staStart: String = "",
    @field:XmlAttribute override val length: String = "",
    @field:XmlAttribute override val oID: String? = null,
    @field:XmlAttribute override val rot: String = "",
    @field:XmlAttribute override val constant: String? = null,
    @field:XmlAttribute override val dirStart: String? = null,
    @field:XmlAttribute override val dirEnd: String? = null,
    @field:XmlAttribute override val radiusStart: String? = null,
    @field:XmlAttribute override val radiusEnd: String? = null,
    @field:XmlAttribute override val spiType: String = "",

    @field:XmlElement(name = "Start", namespace = XMLNS_404)
    override val start: String = "",
    @field:XmlElement(name = "PI", namespace = XMLNS_404)
    override val pi: String = "",
    @field:XmlElement(name = "End", namespace = XMLNS_404)
    override val end: String = "",
    @field:XmlElement(name = "Feature", namespace = XMLNS_404)
    override val features: List<InfraModelFeature404> = arrayListOf(),
) : InfraModelSpiral

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelCant404(
    @field:XmlAttribute override val name: String = "",
    @field:XmlAttribute override val desc: String? = null,
    @field:XmlAttribute override val gauge: String = "",
    @field:XmlAttribute override val rotationPoint: String = "",

    @field:XmlElement(name = "CantStation", namespace = XMLNS_404)
    override val stations: List<InfraModelCantStation404> = arrayListOf(),
):InfraModelCant

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelCantStation404(
    @field:XmlAttribute override val station: String = "",
    @field:XmlAttribute override val appliedCant: String = "",
    @field:XmlAttribute override val curvature: String = "",
    @field:XmlAttribute override val transitionType: String? = null,
):InfraModelCantStation

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelProfile404(
    @field:XmlElement(name = "ProfAlign", namespace = XMLNS_404)
    override val profAlign: InfraModelProfAlign404? = null,
    @field:XmlElement(name = "Feature", namespace = XMLNS_404)
    override val features: List<InfraModelFeature404> = arrayListOf(),
):InfraModelProfile

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelProfAlign404(
    @field:XmlAttribute override val name: String = "",

    @field:XmlElements(
        XmlElement(name = "PVI", namespace = XMLNS_404, type = InfraModelPvi404::class),
        XmlElement(name = "CircCurve", namespace = XMLNS_404, type = InfraModelCircCurve404::class)
    )
    override val elements: List<InfraModelProfileElement> = arrayListOf(),

    @field:XmlElement(name = "Feature", namespace = XMLNS_404)
    override val features: List<InfraModelFeature404> = arrayListOf(),
):InfraModelProfAlign


@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelPvi404(
    @field:XmlAttribute override val desc: String? = null,
    @field:XmlValue override val point: String = "",
) : InfraModelPvi,InfraModelProfileElement

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelCircCurve404(
    @field:XmlAttribute override val desc: String? = null,
    @field:XmlAttribute override val length: String = "",
    @field:XmlAttribute override val radius: String = "",
    @field:XmlValue override val point: String = "",
) : InfraModelCircCurve, InfraModelProfileElement

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelFeature404(
    @field:XmlAttribute override val code: String = "",

    @field:XmlElement(name = "Property", namespace = XMLNS_404)
    override val properties: List<InfraModelProperty404> = arrayListOf(),
):InfraModelFeature {
    override fun getProperty(label: String): String {
        return properties.find { p -> p.label == label }?.value
            ?: throw InframodelParsingException("Feature $code doesn't have property $label")
    }

    override fun getPropertyAnyMatch(labels: List<String>): String? {
        return properties.find { p -> labels.contains(p.label) }?.value
    }
}

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelProperty404(
    @field:XmlAttribute override val label: String = "",
    @field:XmlAttribute override val value: String = "",
):InfraModelProperty
