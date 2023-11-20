package fi.fta.geoviite.infra.tievelho

import fi.fta.geoviite.infra.tievelho.generated.model.TVNimikkeistoVarusteetMateriaali

// TODO: POC this should really be done by syncing the dictionaries from metadata API

fun getMateriaali(value: String): TVNimikkeistoVarusteetMateriaali = when (value) {
    "Alumiini" -> TVNimikkeistoVarusteetMateriaali.ma01
    "Vaneri" -> TVNimikkeistoVarusteetMateriaali.ma02
    "Lasi" -> TVNimikkeistoVarusteetMateriaali.ma03
    "Puu" -> TVNimikkeistoVarusteetMateriaali.ma04
    "Teräs" -> TVNimikkeistoVarusteetMateriaali.ma05
    "Valurauta" -> TVNimikkeistoVarusteetMateriaali.ma06
    "Maa-aines" -> TVNimikkeistoVarusteetMateriaali.ma07
    "Muu" -> TVNimikkeistoVarusteetMateriaali.ma08
    "Betoni" -> TVNimikkeistoVarusteetMateriaali.ma09
    "Muovi" -> TVNimikkeistoVarusteetMateriaali.ma10
    "Kivi" -> TVNimikkeistoVarusteetMateriaali.ma12
    "Pleksi" -> TVNimikkeistoVarusteetMateriaali.ma13
    "Lasikuitu" -> TVNimikkeistoVarusteetMateriaali.ma14
    "Riistaverkko" -> TVNimikkeistoVarusteetMateriaali.ma15
    "Panssariverkko" -> TVNimikkeistoVarusteetMateriaali.ma16
    "Metalli" -> TVNimikkeistoVarusteetMateriaali.ma17
    "Asfaltti" -> TVNimikkeistoVarusteetMateriaali.ma18
    "Alumiinikomposiitti" -> TVNimikkeistoVarusteetMateriaali.ma19
    else -> throw IllegalArgumentException("Cannot map value to ${TVNimikkeistoVarusteetMateriaali::class.simpleName}: value=$value")
}
