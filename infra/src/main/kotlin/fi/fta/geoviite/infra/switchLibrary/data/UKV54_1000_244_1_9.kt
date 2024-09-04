package fi.fta.geoviite.infra.switchLibrary.data

import fi.fta.geoviite.infra.switchLibrary.SwitchType

fun UKV54_1000_244_1_9_O() = UKV60_1000_244_1_9_O().copy(type = SwitchType("UKV54-1000/244-1:9-O"))

fun UKV54_1000_244_1_9_V() = UKV54_1000_244_1_9_O().flipAlongYAxis().copy(type = SwitchType("UKV54-1000/244-1:9-V"))
