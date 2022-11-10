package fi.fta.geoviite.infra.dataImport

import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.switchLibrary.data.*
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource


val switchStructures: List<SwitchStructure> by lazy {
    listOf(
        KRV43_233_1_9(),
        KRV43_270_1_9_514(),
        KRV54_200_1_9(),

        KV30_270_1_9_514_O(),
        KV30_270_1_9_514_V(),
        KV43_300_1_9_514_O(),
        KV43_300_1_9_514_V(),
        KV54_200_1_9_O(),
        KV54_200_1_9_V(),
        KV54_200N_1_9_O(),
        KV54_200N_1_9_V(),

        RR54_1_9(),
        RR54_2x1_9(),

        SKV60_800_423_1_15_5_O(),
        SKV60_800_423_1_15_5_V(),
        SKV60_1000_474_1_15_5_O(),
        SKV60_1000_474_1_15_5_V(),

        SRR54_2x1_9_4_8(),
        SRR54_2x1_9_6_0(),
        SRR60_2x1_9_4_8(),

        TYV54_200_1_4_44(),
        TYV54_200_1_4_44TPE(),
        TYV54_225_1_6_46(),
        TYV54_225_1_6_46TPE(),

        UKV54_800_258_1_9_O(),
        UKV54_800_258_1_9_V(),
        UKV54_1000_244_1_9_O(),
        UKV54_1000_244_1_9_V(),
        UKV54_1500_228_1_9_O(),
        UKV54_1500_228_1_9_V(),
        UKV60_600_281_1_9_O(),
        UKV60_600_281_1_9_V(),
        UKV60_1000_244_1_9_O(),
        UKV60_1000_244_1_9_V(),

        YRV54_200_1_9(),

        YV30_270_1_7_O(),
        YV30_270_1_7_V(),
        YV30_270_1_9_514_O(),
        YV30_270_1_9_514_V(),

        YV43_205_1_9_O(),
        YV43_205_1_9_V(),
        YV43_205_1_9_514_O(),
        YV43_205_1_9_514_V(),
        YV43_300_1_7_O(),
        YV43_300_1_7_V(),
        YV43_300_1_9_O(),
        YV43_300_1_9_V(),
        YV43_300_1_9_514_O(),
        YV43_300_1_9_514_V(),
        YV43_300_1_9_514_1435_O(),
        YV43_300_1_9_514_1435_V(),
        YV43_530_1_15_O(),
        YV43_530_1_15_V(),

        YV54_165_1_7_O(),
        YV54_165_1_7_V(),
        YV54_190_1_7_O(),
        YV54_190_1_7_V(),
        YV54_200_1_9_O(),
        YV54_200_1_9_V(),
        YV54_200_1_9_1435_O(),
        YV54_200_1_9_1435_V(),
        YV54_200N_1_9_O(),
        YV54_200N_1_9_V(),
        YV54_200N_1_9_1435_O(),
        YV54_200N_1_9_1435_V(),
        YV54_200_1_9_1524_1435_O(),
        YV54_200_1_9_1524_1435_V(),
        YV54_900_1_15_5_O(),
        YV54_900_1_15_5_V(),

        YV60_300_1_9_O(),
        YV60_300_1_9_V(),
        YV60_300A_1_9_O(),
        YV60_300A_1_9_V(),
        YV60_300E_1_9_O(),
        YV60_300E_1_9_V(),
        YV60_300P_1_9_O(),
        YV60_300P_1_9_V(),
        YV60_300_1_10_O(),
        YV60_300_1_10_V(),
        YV60_500_1_11_1_O(),
        YV60_500_1_11_1_V(),
        YV60_500A_1_11_1_O(),
        YV60_500A_1_11_1_V(),
        YV60_500_1_14_O(),
        YV60_500_1_14_V(),
        YV60_500A_1_14_O(),
        YV60_500A_1_14_V(),
        YV60_900_1_15_5_O(),
        YV60_900_1_15_5_V(),
        YV60_900A_1_15_5_O(),
        YV60_900A_1_15_5_V(),
        YV60_900E_1_15_5_O(),
        YV60_900E_1_15_5_V(),
        YV60_900_1_18_O(),
        YV60_900_1_18_V(),
        YV60_900A_1_18_O(),
        YV60_900A_1_18_V(),
        YV60_900P_1_18_O(),
        YV60_900P_1_18_V(),
        YV60_5000_2500_1_26_O(),
        YV60_5000_2500_1_26_V(),
        YV60_5000_3000_1_28_O(),
        YV60_5000_3000_1_28_V(),
    )
}

val inframodelAliases = mapOf(
    "YV54-200(N)-1:9" to "YV54-200N-1:9",
    "YV60-2500-1:26" to "YV60-5000/2500-1:26",
    "YV60-3000-1:28" to "YV60-5000/3000-1:28",
)

@Suppress("unused", "ClassName")
class V10_03_06__SwitchLibraryDataMigration : BaseJavaMigration() {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun migrate(context: Context?) = withUser(ImportUser.SWITCH_LIB_IMPORT) {
        logger.info("Add switch structures into library")

        val connection = context?.connection
            ?: throw IllegalStateException("Can't run imports without DB connection")
        val jdbcTemplate = NamedParameterJdbcTemplate(
            SingleConnectionDataSource(connection, true)
        )
        val switchStructureDao = SwitchStructureDao(jdbcTemplate)
        switchStructures.forEach(switchStructureDao::insertSwitchStructure)
        inframodelAliases.forEach { (alias, type) ->
            switchStructureDao.insertInframodelAlias(alias, type)
        }
    }

    // Increase this manually when switch library data changes
    override fun getChecksum(): Int = 6
}
