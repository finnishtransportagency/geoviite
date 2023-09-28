package fi.fta.geoviite.infra.ifc

fun ifc(vararg dataLines: IfcDataEntity) = Ifc(
    header = IfcHeader(),
    data = IfcData(dataLines.associateBy { dl -> dl.id }),
)

fun ifcDataEntity(id: Int, name: String, vararg content: IfcEntityAttribute) =
    IfcDataEntity(ifcId(id), IfcEntity(ifcName(name), IfcEntityList(content.toList())))

fun ifcId(idNumber: Int) = IfcEntityId("${IfcEntityId.PREFIX}$idNumber")

fun ifcName(value: String) = IfcName.valueOf(value)

fun ifcEnum(value: String) = IfcEntityEnum.valueOf(value)

fun ifcNumber(value: Int) = IfcEntityNumber(value.toBigDecimal())

fun ifcNumber(value: Double) = IfcEntityNumber(value.toBigDecimal())

fun ifcString(value: String) = IfcEntityString(value)
