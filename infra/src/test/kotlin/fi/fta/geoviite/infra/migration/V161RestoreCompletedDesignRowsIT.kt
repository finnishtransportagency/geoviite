package fi.fta.geoviite.infra.migration

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationCause
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationMessage
import fi.fta.geoviite.infra.tracklayout.DesignAssetState
import fi.fta.geoviite.infra.tracklayout.DesignOfficialContextData
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.operationalPoint
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class V161RestoreCompletedDesignRowsIT
@Autowired
constructor(private val publicationDao: PublicationDao, private val locationTrackService: LocationTrackService) :
    DBTestBase() {

    private data class AssetRow(val table: String, val id: Int, val version: Int)

    private data class VersionRow(val version: Int, val deleted: Boolean, val state: String?, val expired: Boolean)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `deleted COMPLETED design-official rows are restored at their COMPLETED version`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficial = testDBService.testContext(designBranch, OFFICIAL)
        val segment = segment(Point(0.0, 0.0), Point(0.0, 2.0))
        val trackNumber = designOfficial.save(trackNumber(), referenceLineGeometry(segment))
        val locationTrack = designOfficial.save(locationTrack(trackNumber.id), trackGeometryOfSegments(segment))

        val completedRows =
            listOf(
                completeAndDelete("track_number", trackNumber),
                completeAndDelete("location_track", locationTrack),
                completeAndDelete("switch", designOfficial.save(switch())),
                completeAndDelete("km_post", designOfficial.save(kmPost(trackNumber.id, KmNumber(1)))),
                completeAndDelete("operational_point", designOfficial.save(operationalPoint())),
            )
        completedRows.forEach { row -> assertNull(liveRow(row, designBranch)) }

        runMigration()

        completedRows.forEach { row ->
            assertEquals(row.version to "COMPLETED", liveRow(row, designBranch), "live row of $row")
            versionRows(row).last().also { latest ->
                assertEquals(VersionRow(row.version, false, "COMPLETED", false), latest, "latest version of $row")
            }
        }
        // Child rows are keyed to the restored version, so the geometry still resolves through it
        val restoredTrack = completedRows.single { it.table == "location_track" }
        locationTrackService.getWithGeometry(LayoutRowVersion(locationTrack.rowId, restoredTrack.version)).also {
            (_, geometry) ->
            assertTrue(geometry.edges.isNotEmpty())
        }
    }

    @Test
    fun `OPEN and CANCELLED design rows are untouched`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficial = testDBService.testContext(designBranch, OFFICIAL)
        val openSwitch = designOfficial.save(switch())
        val cancelledSwitch = markDesignOfficial(designOfficial.save(switch()), DesignAssetState.CANCELLED)
        val open = AssetRow("switch", openSwitch.id.intValue, openSwitch.version)
        val cancelled = AssetRow("switch", cancelledSwitch.id.intValue, cancelledSwitch.version)
        val before = listOf(open, cancelled).associateWith { row -> liveRow(row, designBranch) to versionRows(row) }

        runMigration()

        before.forEach { (row, state) -> assertEquals(state, liveRow(row, designBranch) to versionRows(row)) }
    }

    @Test
    fun `a completed chain that was edited again in the design is untouched`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficial = testDBService.testContext(designBranch, OFFICIAL)
        val mainSwitch = mainOfficialContext.save(switch())
        val completed = completeAndDelete("switch", designOfficial.copyFrom(mainSwitch))
        designOfficial.copyFrom(mainSwitch)
        val before = liveRow(completed, designBranch) to versionRows(completed)
        assertEquals("OPEN", before.first?.second)

        runMigration()

        assertEquals(before, liveRow(completed, designBranch) to versionRows(completed))
    }

    @Test
    fun `publication references to the removed version are re-pointed to the restored version`() {
        val designBranch = testDBService.createDesignBranch()
        val designOfficial = testDBService.testContext(designBranch, OFFICIAL)
        val segment = segment(Point(0.0, 0.0), Point(0.0, 2.0))
        val trackNumber = designOfficial.save(trackNumber(), referenceLineGeometry(segment))
        val locationTrack = designOfficial.save(locationTrack(trackNumber.id), trackGeometryOfSegments(segment))
        val rows =
            listOf(
                completeAndDelete("track_number", trackNumber),
                completeAndDelete("location_track", locationTrack),
                completeAndDelete("switch", designOfficial.save(switch())),
                completeAndDelete("km_post", designOfficial.save(kmPost(trackNumber.id, KmNumber(1)))),
                completeAndDelete("operational_point", designOfficial.save(operationalPoint())),
            )
        val publicationId =
            publicationDao
                .createPublication(designBranch, PublicationMessage.of("test"), PublicationCause.MANUAL, null)
                .intValue
        rows.forEach { row -> insertPublicationRow(publicationId, row, row.version + 1) }
        val track = rows.single { it.table == "location_track" }
        val switch = rows.single { it.table == "switch" }
        insertSwitchLocationTrack(publicationId, switch, track, track.version + 1)

        runMigration()

        rows.forEach { row ->
            assertEquals(row.version to row.version, publicationRowVersions(publicationId, row), "publication of $row")
        }
        assertEquals(track.version, switchLocationTrackVersion(publicationId, switch, track))
    }

    /** Reproduces what publishing a completion did before GVT-3767: COMPLETED official row, then deleted. */
    private inline fun <reified T : LayoutAsset<T>> completeAndDelete(
        table: String,
        designOfficialVersion: LayoutRowVersion<T>,
    ): AssetRow {
        val completed = markDesignOfficial(designOfficialVersion, DesignAssetState.COMPLETED)
        testDBService.delete(completed)
        return AssetRow(table, completed.id.intValue, completed.version)
    }

    private inline fun <reified T : LayoutAsset<T>> markDesignOfficial(
        version: LayoutRowVersion<T>,
        state: DesignAssetState,
    ): LayoutRowVersion<T> =
        testDBService.update(version) { asset ->
            asset.withContext((asset.contextData as DesignOfficialContextData<T>).copy(designAssetState = state))
        }

    private fun runMigration() {
        val sql =
            javaClass.classLoader.getResource("db/migration/prod/V161__restore_completed_design_rows.sql")!!.readText()
        val statements =
            sql.split(";")
                .map { chunk -> chunk.lines().filterNot { it.trim().startsWith("--") }.joinToString("\n").trim() }
                .filter { it.isNotEmpty() }
        transactional { statements.forEach { jdbc.jdbcTemplate.execute(it) } }
    }

    private fun liveRow(row: AssetRow, designBranch: DesignBranch): Pair<Int, String>? =
        jdbc
            .query(
                """
                select version, design_asset_state
                from layout.${row.table}
                where id = :id and design_id = :design_id and not draft
                """
                    .trimIndent(),
                mapOf("id" to row.id, "design_id" to designBranch.designId.intValue),
            ) { rs, _ ->
                rs.getInt("version") to rs.getString("design_asset_state")
            }
            .firstOrNull()

    private fun versionRows(row: AssetRow): List<VersionRow> =
        jdbc.query(
            """
            select version, deleted, design_asset_state, expiry_time is not null as expired
            from layout.${row.table}_version
            where id = :id
              and layout_context_id = (select layout_context_id from layout.${row.table}_version where id = :id and version = :version)
            order by version
            """
                .trimIndent(),
            mapOf("id" to row.id, "version" to row.version),
        ) { rs, _ ->
            VersionRow(
                rs.getInt("version"),
                rs.getBoolean("deleted"),
                rs.getString("design_asset_state"),
                rs.getBoolean("expired"),
            )
        }

    private fun insertPublicationRow(publicationId: Int, row: AssetRow, version: Int) {
        val extraColumns =
            when (row.table) {
                "track_number",
                "location_track" -> mapOf("start_changed" to false, "end_changed" to false, "direct_change" to true)
                "switch" -> mapOf("direct_change" to true)
                else -> mapOf()
            }
        val columns = extraColumns.keys.joinToString("") { column -> ", $column" }
        val values = extraColumns.keys.joinToString("") { column -> ", :$column" }
        jdbc.update(
            """
            insert into publication.${row.table}
              (publication_id, id, version, layout_context_id, base_version, base_layout_context_id$columns)
            select :publication_id, id, version, layout_context_id, version, layout_context_id$values
              from layout.${row.table}_version
              where id = :id
                and version = :version
                and layout_context_id = (select layout_context_id from layout.${row.table}_version where id = :id and version = :restored_version)
            """
                .trimIndent(),
            mapOf(
                "publication_id" to publicationId,
                "id" to row.id,
                "version" to version,
                "restored_version" to row.version,
            ) + extraColumns,
        )
    }

    private fun insertSwitchLocationTrack(publicationId: Int, switch: AssetRow, track: AssetRow, trackVersion: Int) {
        jdbc.update(
            """
            insert into publication.switch_location_tracks
              (publication_id, switch_id, location_track_id, location_track_version, is_topology_switch,
               location_track_layout_context_id)
            select :publication_id, :switch_id, id, version, false, layout_context_id
              from layout.location_track_version
              where id = :track_id
                and version = :track_version
                and layout_context_id = (select layout_context_id from layout.location_track_version where id = :track_id and version = :restored_version)
            """
                .trimIndent(),
            mapOf(
                "publication_id" to publicationId,
                "switch_id" to switch.id,
                "track_id" to track.id,
                "track_version" to trackVersion,
                "restored_version" to track.version,
            ),
        )
    }

    private fun publicationRowVersions(publicationId: Int, row: AssetRow): Pair<Int, Int> =
        jdbc
            .query(
                "select version, base_version from publication.${row.table} where publication_id = :publication_id and id = :id",
                mapOf("publication_id" to publicationId, "id" to row.id),
            ) { rs, _ ->
                rs.getInt("version") to rs.getInt("base_version")
            }
            .single()

    private fun switchLocationTrackVersion(publicationId: Int, switch: AssetRow, track: AssetRow): Int =
        jdbc
            .query(
                """
                select location_track_version from publication.switch_location_tracks
                where publication_id = :publication_id and switch_id = :switch_id and location_track_id = :track_id
                """
                    .trimIndent(),
                mapOf("publication_id" to publicationId, "switch_id" to switch.id, "track_id" to track.id),
            ) { rs, _ ->
                rs.getInt("location_track_version")
            }
            .single()
}
