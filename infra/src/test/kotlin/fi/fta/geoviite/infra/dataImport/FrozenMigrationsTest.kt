package fi.fta.geoviite.infra.dataImport

import kotlin.test.assertEquals
import org.flywaydb.core.internal.resolver.ChecksumCalculator
import org.flywaydb.core.internal.resource.StringResource
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test", "nodb")
@SpringBootTest
class FrozenMigrationsTest @Autowired constructor(val resourceResolver: ResourcePatternResolver) {
    private data class MigrationFile(val fileName: String, val checksum: Int)

    @Test
    fun initMigrationsHaveNotChanged() {
        // This test is a sanity-check to give early warning for developers if they
        // accidentally edit the un-editable: the initial migrations
        val expectedMigrations: List<MigrationFile> =
            listOf(
                MigrationFile(fileName = "V00.01__install_postgis.sql", checksum = 1412735420),
                MigrationFile(fileName = "V00.10__common_utility_functions.sql", checksum = 1115603267),
                MigrationFile(fileName = "V00.11__common_metadata_functions.sql", checksum = 1301034460),
                MigrationFile(fileName = "V01.01__common_unit_type.sql", checksum = -407838513),
                MigrationFile(fileName = "V01.02__common_feature_type.sql", checksum = 2106249120),
                MigrationFile(fileName = "V01.03.01__common_switch_type.sql", checksum = -43494992),
                MigrationFile(fileName = "V01.03.02__common_switch_structure.sql", checksum = -1109698266),
                MigrationFile(fileName = "V01.03.03__common_switch_alignment.sql", checksum = 487568140),
                MigrationFile(fileName = "V01.03.04__common_switch_element.sql", checksum = 1028870762),
                MigrationFile(fileName = "V01.03.05__common_switch_joint.sql", checksum = 1959336719),
                MigrationFile(fileName = "V01.03.06__common_switch_owner.sql", checksum = -39678304),
                MigrationFile(fileName = "V01.04__common_coordinate_system.sql", checksum = 1752767082),
                MigrationFile(fileName = "V01.05__common_location_accuracy.sql", checksum = -999072516),
                MigrationFile(fileName = "V01.06__common_vertical_coordinate_system.sql", checksum = 1647683084),
                MigrationFile(
                    fileName = "V01.03.07__common_inframodel_switch_type_name_alias.sql",
                    checksum = 144978698,
                ),
                MigrationFile(fileName = "V01.07.01__common_role.sql", checksum = -410660863),
                MigrationFile(fileName = "V01.07.02__common_privilege.sql", checksum = 899063532),
                MigrationFile(fileName = "V01.07.03__common_role_privilege.sql", checksum = -1711975293),
                MigrationFile(fileName = "V01.08.01__layout_state.sql", checksum = 1058871688),
                MigrationFile(fileName = "V01.08.02__layout_track_number.sql", checksum = -1293075289),
                MigrationFile(fileName = "V01.08.03__layout_category.sql", checksum = -654332391),
                MigrationFile(fileName = "V02.00.01__geometry_plan_state.sql", checksum = 494877868),
                MigrationFile(fileName = "V02.00.02__geometry_plan_source.sql", checksum = -1657379807),
                MigrationFile(fileName = "V02.01.01__geometry_plan_project.sql", checksum = -1922121912),
                MigrationFile(fileName = "V02.01.02__geometry_plan_application.sql", checksum = 626252729),
                MigrationFile(fileName = "V02.01.03__geometry_plan_author.sql", checksum = -1281457289),
                MigrationFile(fileName = "V02.01.04__geometry_plan.sql", checksum = 978092024),
                MigrationFile(fileName = "V02.01.05__geometry_plan_file.sql", checksum = -1529850039),
                MigrationFile(fileName = "V02.02.01__geometry_km_post.sql", checksum = -311372319),
                MigrationFile(fileName = "V02.03.01__geometry_switch.sql", checksum = 273160783),
                MigrationFile(fileName = "V02.03.02__geometry_switch_joint.sql", checksum = 1912952394),
                MigrationFile(fileName = "V02.04.01__geometry_alignment.sql", checksum = 1326585480),
                MigrationFile(fileName = "V02.04.02__geometry_element.sql", checksum = -1328250198),
                MigrationFile(fileName = "V02.04.03__geometry_vertical_intersection.sql", checksum = 502980499),
                MigrationFile(fileName = "V02.04.04__geometry_cant_point.sql", checksum = -1255272875),
                MigrationFile(fileName = "V03.02.01__layout_switch.sql", checksum = 886649753),
                MigrationFile(fileName = "V03.02.02__layout_switch_joint.sql", checksum = 838150452),
                MigrationFile(fileName = "V03.03.01__layout_km_post.sql", checksum = 605416016),
                MigrationFile(fileName = "V03.04.01__layout_alignment.sql", checksum = 1616389042),
                MigrationFile(fileName = "V03.04.02__layout_segment.sql", checksum = 190129497),
                MigrationFile(fileName = "V03.05.01__layout_location_track.sql", checksum = -1925261205),
                MigrationFile(fileName = "V03.05.02__layout_reference_line.sql", checksum = 1815023431),
                MigrationFile(fileName = "V04.01.01__layout_initial_import_metadata.sql", checksum = 51863216),
                MigrationFile(fileName = "V10.01__common_feature_type_values.sql", checksum = 1021841055),
                MigrationFile(fileName = "V10.02__common_coordinate_system_values.sql", checksum = 1382442814),
                MigrationFile(fileName = "V10.03.04__common_switch_owner_values.sql", checksum = 1821224761),
                MigrationFile(fileName = "V10.04.01__common_n60_n200_triangulation_network.sql", checksum = -801851226),
                MigrationFile(
                    fileName = "V10.04.02__common_inserts_for_n60_n2000_triangle_corner_point_table.sql",
                    checksum = -1896455933,
                ),
                MigrationFile(
                    fileName = "V10.04.03__common_inserts_for_n60_n2000_triangulation_network_table.sql",
                    checksum = 1910613803,
                ),
                MigrationFile(
                    fileName = "V10.05.01__common_kkj_etrs_triangulation_network.sql",
                    checksum = -1040194410,
                ),
                MigrationFile(
                    fileName = "V10.05.02__common_inserts_for_kkj_etrs_triangle_corner_point_table.sql",
                    checksum = -294904538,
                ),
                MigrationFile(
                    fileName = "V10.05.03__common_inserts_for_kkj_etrs_triangulation_network.sql",
                    checksum = 571127275,
                ),
                MigrationFile(fileName = "V11.00.00__layout_track_number_imports_in_code.sql", checksum = 465780583),
                MigrationFile(fileName = "V12.00.00__geometry_inframodel_imports_in_code.sql", checksum = 1765353731),
                MigrationFile(fileName = "V13.01.01__finalize_geometry_tables.sql", checksum = 4829361),
                MigrationFile(fileName = "V14.00.00__layout_imports_in_code.sql", checksum = 1185536260),
                MigrationFile(fileName = "V15.01.01__finalize_layout_tables.sql", checksum = 493331486),
                MigrationFile(fileName = "V16.01.01__publication.sql", checksum = -257379860),
                MigrationFile(fileName = "V16.02.01__publication_calculated_changes.sql", checksum = 640208248),
                MigrationFile(fileName = "V17.01.01__integrations_enums.sql", checksum = 332715753),
                MigrationFile(fileName = "V17.01.02__integrations_lock.sql", checksum = -316859793),
                MigrationFile(fileName = "V17.01.03__integrations_ratko_push.sql", checksum = -1142466586),
                MigrationFile(fileName = "V17.01.04__integrations_ratko_push_content.sql", checksum = 485117446),
                MigrationFile(fileName = "V17.01.05__integrations_ratko_push_error.sql", checksum = 578997775),
                MigrationFile(fileName = "V17.02.00__publication_add_first_publication.sql", checksum = -1157951969),
            )
        val allMigrations = collectAllMigrations("init")

        // Verify one-by-one for clearer error message

        // Existing migrations cannot change
        expectedMigrations.forEach { expected ->
            val actual = allMigrations.find { actual -> expected.fileName == actual.fileName }
            assertEquals(expected, actual, "Existing migration changed: old=$expected new=$actual")
        }

        // New migrations cannot be added to init directory
        allMigrations.forEach { actual ->
            val expectedExists = expectedMigrations.any { expected -> expected.fileName == actual.fileName }
            assertTrue(expectedExists, "New migration found in init: new=$actual")
        }

        // Code migrations must also retain their versions, names and checksums
        val csvMigrations =
            listOf(
                V11_01__Csv_import_track_numbers() to "V11_01__Csv_import_track_numbers",
                V14_01__Csv_import_km_posts() to "V14_01__Csv_import_km_posts",
                V14_02__Csv_import_switches() to "V14_02__Csv_import_switches",
                V14_03__Csv_import_reference_lines() to "V14_03__Csv_import_reference_lines",
                V14_04__Csv_import_location_tracks() to "V14_04__Csv_import_location_tracks",
            )
        csvMigrations.forEach { (migration, name) ->
            assertEquals(migration.checksum, 9)
            assertEquals(name, migration::class.simpleName)
        }
        assertEquals(V12_01__InfraModelMigration().checksum, null)
        assertEquals("V12_01__InfraModelMigration", V12_01__InfraModelMigration::class.simpleName)
        assertEquals(V10_03_06__SwitchLibraryDataMigration().checksum, 6)
        assertEquals("V10_03_06__SwitchLibraryDataMigration", V10_03_06__SwitchLibraryDataMigration::class.simpleName)
    }

    private fun collectAllMigrations(folder: String): List<MigrationFile> =
        resourceResolver.getResources("classpath:db/migration/$folder/*.sql").map { resource ->
            MigrationFile(
                fileName = resource.filename!!,
                checksum = ChecksumCalculator.calculate(StringResource(resource.file.readText())),
            )
        }
}
