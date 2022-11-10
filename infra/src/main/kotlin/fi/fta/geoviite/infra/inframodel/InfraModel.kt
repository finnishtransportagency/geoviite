package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.error.InframodelParsingException
import jakarta.xml.bind.annotation.*

const val XMLNS_403 = "http://www.inframodel.fi/inframodel"
const val XMLNS_404 = "http://buildingsmart.fi/inframodel/404"
const val XMLNS = XMLNS_403

@XmlRootElement(name = "LandXML", namespace = XMLNS)
@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModel(
    @field:XmlAttribute val language: String? = null,

    @field:XmlElement(name = "FeatureDictionary", namespace = XMLNS)
    val featureDictionary: InfraModelFeatureDictionary? = null,
    @field:XmlElement(name = "Units", namespace = XMLNS)
    val units: InfraModelUnits? = null,
    @field:XmlElement(name = "CoordinateSystem", namespace = XMLNS)
    val coordinateSystem: InfraModelCoordinateSystem? = null,
    @field:XmlElement(name = "Project", namespace = XMLNS)
    val project: InfraModelProject? = null,
    @field:XmlElement(name = "Application", namespace = XMLNS)
    val application: InfraModelApplication? = null,
    @field:XmlElement(name = "Alignments", namespace = XMLNS)
    val alignmentGroups: List<InfraModelAlignmentGroup> = arrayListOf(),
)

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelFeatureDictionary(
    @field:XmlAttribute val name: String = "",
)

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelUnits(
    @field:XmlElement(name = "Metric", namespace = XMLNS)
    val metric: InfraModelMetric? = null,
)

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelMetric(
    @field:XmlAttribute val linearUnit: String = "",
    @field:XmlAttribute val areaUnit: String = "",
    @field:XmlAttribute val volumeUnit: String = "",
    @field:XmlAttribute val angularUnit: String = "",
    @field:XmlAttribute val directionUnit: String = "",
)

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelProject(
    @field:XmlAttribute val name: String = "",
    @field:XmlAttribute val desc: String? = null,
)

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelApplication(
    @field:XmlAttribute val name: String = "",
    @field:XmlAttribute val desc: String? = null,
    @field:XmlAttribute val manufacturer: String = "",
    @field:XmlAttribute val manufacturerURL: String = "",
    @field:XmlAttribute val version: String = "",

    @field:XmlElement(name = "Author", namespace = XMLNS)
    val author: InfraModelAuthor? = null,
)

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelAuthor(
    @field:XmlAttribute val createdBy: String? = null,
    @field:XmlAttribute val createdByEmail: String? = null,
    @field:XmlAttribute val company: String? = null,
    @field:XmlAttribute val companyUR: String? = null,
    @field:XmlAttribute val timeStamp: String? = null,
)

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelCoordinateSystem(
    @field:XmlAttribute val name: String = "",
    @field:XmlAttribute val epsgCode: String = "",
    @field:XmlAttribute val verticalCoordinateSystemName: String = "",
    @field:XmlAttribute val rotationAngle: String = "",
)

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelAlignmentGroup(
    @field:XmlAttribute val name: String = "",
    @field:XmlAttribute val desc: String? = null,
    @field:XmlAttribute val state: String? = null,
    @field:XmlElement(name = "Alignment", namespace = XMLNS)
    val alignments: List<InfraModelAlignment> = arrayListOf(),
)

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelAlignment(
    @field:XmlAttribute val name: String = "",
    @field:XmlAttribute val desc: String? = null,
    @field:XmlAttribute val state: String? = null,
    @field:XmlAttribute val oid: String? = null,
    @field:XmlAttribute val staStart: String = "",

    @field:XmlElementWrapper(name = "CoordGeom", namespace = XMLNS)
    @field:XmlElements(
        XmlElement(name = "Line", namespace = XMLNS, type = InfraModelLine::class),
        XmlElement(name = "Curve", namespace = XMLNS, type = InfraModelCurve::class),
        XmlElement(name = "Spiral", namespace = XMLNS, type = InfraModelSpiral::class)
    )
    val elements: List<InfraModelGeometryElement> = arrayListOf(),

    @field:XmlElement(name = "Cant", namespace = XMLNS)
    val cant: InfraModelCant? = null,

    @field:XmlElement(name = "Profile", namespace = XMLNS)
    val profile: InfraModelProfile? = null,

    @field:XmlElement(name = "Feature", namespace = XMLNS)
    val features: List<InfraModelFeature> = arrayListOf(),

    @field:XmlElement(name = "StaEquation", namespace = XMLNS)
    val staEquations: List<InfraModelStaEquation> = arrayListOf(),
)

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelStaEquation(
    @field:XmlAttribute val staBack: String = "",
    @field:XmlAttribute val staAhead: String = "",
    @field:XmlAttribute val staInternal: String = "",
    @field:XmlAttribute val desc: String = "",
    @field:XmlElement(name = "Feature", namespace = XMLNS)
    val feature: InfraModelFeature? = null,
)

sealed class InfraModelGeometryElement {
    abstract val name: String?
    abstract val oID: String?
    abstract val staStart: String
    abstract val length: String
    abstract val start: String
    abstract val end: String
    abstract val features: List<InfraModelFeature>
}

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelLine(
    @field:XmlAttribute override val name: String? = null,
    @field:XmlAttribute override val staStart: String = "",
    @field:XmlAttribute override val length: String = "",
    @field:XmlAttribute override val oID: String? = null,

    @field:XmlElement(name = "Start", namespace = XMLNS)
    override val start: String = "",
    @field:XmlElement(name = "End", namespace = XMLNS)
    override val end: String = "",
    @field:XmlElement(name = "Feature", namespace = XMLNS)
    override val features: List<InfraModelFeature> = arrayListOf(),
) : InfraModelGeometryElement()

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelCurve(
    @field:XmlAttribute override val name: String? = null,
    @field:XmlAttribute override val staStart: String = "",
    @field:XmlAttribute override val length: String = "",
    @field:XmlAttribute override val oID: String? = null,
    @field:XmlAttribute val rot: String = "",
    @field:XmlAttribute val radius: String = "",
    @field:XmlAttribute val chord: String = "",

    @field:XmlElement(name = "Start", namespace = XMLNS)
    override val start: String = "",
    @field:XmlElement(name = "Center", namespace = XMLNS)
    val center: String = "",
    @field:XmlElement(name = "End", namespace = XMLNS)
    override val end: String = "",
    @field:XmlElement(name = "Feature", namespace = XMLNS)
    override val features: List<InfraModelFeature> = arrayListOf(),
) : InfraModelGeometryElement()

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelSpiral(
    @field:XmlAttribute override val name: String? = null,
    @field:XmlAttribute override val staStart: String = "",
    @field:XmlAttribute override val length: String = "",
    @field:XmlAttribute override val oID: String? = null,
    @field:XmlAttribute val rot: String = "",
    @field:XmlAttribute val constant: String? = null,
    @field:XmlAttribute val dirStart: String? = null,
    @field:XmlAttribute val dirEnd: String? = null,
    @field:XmlAttribute val radiusStart: String? = null,
    @field:XmlAttribute val radiusEnd: String? = null,
    @field:XmlAttribute val spiType: String = "",

    @field:XmlElement(name = "Start", namespace = XMLNS)
    override val start: String = "",
    @field:XmlElement(name = "PI", namespace = XMLNS)
    val pi: String = "",
    @field:XmlElement(name = "End", namespace = XMLNS)
    override val end: String = "",
    @field:XmlElement(name = "Feature", namespace = XMLNS)
    override val features: List<InfraModelFeature> = arrayListOf(),
) : InfraModelGeometryElement()

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelCant(
    @field:XmlAttribute val name: String = "",
    @field:XmlAttribute val desc: String? = null,
    @field:XmlAttribute val gauge: String = "",
    @field:XmlAttribute val rotationPoint: String = "",

    @field:XmlElement(name = "CantStation", namespace = XMLNS)
    val stations: List<InfraModelCantStation> = arrayListOf(),
)

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelCantStation(
    @field:XmlAttribute val station: String = "",
    @field:XmlAttribute val appliedCant: String = "",
    @field:XmlAttribute val curvature: String = "",
    @field:XmlAttribute val transitionType: String? = null,
)

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelProfile(
    @field:XmlElement(name = "ProfAlign", namespace = XMLNS)
    val profAlign: InfraModelProfAlign? = null,
    @field:XmlElement(name = "Feature", namespace = XMLNS)
    val features: List<InfraModelFeature> = arrayListOf(),
)

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelProfAlign(
    @field:XmlAttribute val name: String = "",

    @field:XmlElements(
        XmlElement(name = "PVI", namespace = XMLNS, type = InfraModelPvi::class),
        XmlElement(name = "CircCurve", namespace = XMLNS, type = InfraModelCircCurve::class)
    )
    val elements: List<InfraModelProfileElement> = arrayListOf(),

    @field:XmlElement(name = "Feature", namespace = XMLNS)
    val features: List<InfraModelFeature> = arrayListOf(),
)

interface InfraModelProfileElement {
    val desc: String?
    val point: String
}

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelPvi(
    @field:XmlAttribute override val desc: String? = null,
    @field:XmlValue override val point: String = "",
) : InfraModelProfileElement

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelCircCurve(
    @field:XmlAttribute override val desc: String? = null,
    @field:XmlAttribute val length: String = "",
    @field:XmlAttribute val radius: String = "",
    @field:XmlValue override val point: String = "",
) : InfraModelProfileElement

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelFeature(
    @field:XmlAttribute val code: String = "",

    @field:XmlElement(name = "Property", namespace = XMLNS)
    val properties: List<InfraModelProperty> = arrayListOf(),
) {
    fun getProperty(label: String): String {
        return properties.find { p -> p.label == label }?.value
            ?: throw InframodelParsingException("Feature $code doesn't have property $label")
    }

    fun getPropertyAnyMatch(labels: List<String>): String? {
        return properties.find { p -> labels.contains(p.label) }?.value
    }
}

@XmlAccessorType(XmlAccessType.FIELD)
data class InfraModelProperty(
    @field:XmlAttribute val label: String = "",
    @field:XmlAttribute val value: String = "",
)
