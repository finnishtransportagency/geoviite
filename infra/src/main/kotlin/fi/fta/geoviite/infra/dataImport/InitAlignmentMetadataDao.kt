package fi.fta.geoviite.infra.dataImport

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getIntId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InitAlignmentMetadataDao @Autowired constructor(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun <T> insertMetadata(metadataList: List<AlignmentCsvMetaData<T>>): List<IntId<AlignmentCsvMetaData<T>>> {
        val sql = """
            insert into layout.initial_import_metadata(
              alignment_external_id,
              metadata_external_id,
              track_address_start,
              track_address_end,
              measurement_method,
              plan_file_name,
              plan_alignment_name,
              created_year,
              original_crs,
              geometry_alignment_id
            ) 
            values (
              :alignment_external_id,
              :metadata_external_id,
              :track_address_start,
              :track_address_end,
              :measurement_method,
              :plan_file_name,
              :plan_alignment_name,
              :created_year,
              :original_crs,
              :geometry_alignment_id
            )
            returning id
        """.trimIndent()
        return metadataList.map { metadata ->
            val params = mapOf(
                "alignment_external_id" to metadata.alignmentOid.stringValue,
                "metadata_external_id" to metadata.metadataOid?.stringValue,
                "track_address_start" to metadata.startMeter.format(),
                "track_address_end" to metadata.endMeter.format(),
                "measurement_method" to metadata.measurementMethod,
                "plan_file_name" to metadata.fileName.value,
                "plan_alignment_name" to metadata.planAlignmentName.value,
                "created_year" to metadata.createdYear,
                "original_crs" to metadata.originalCrs,
                "geometry_alignment_id" to metadata.geometry?.id?.intValue,
            )
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getIntId("id") }
                ?: throw IllegalStateException("Failed to get ID for new metadata row")
        }
    }

    @Transactional
    fun <T> linkMetadata(
        alignmentId: IntId<LayoutAlignment>,
        segmentMetadataIds: List<IntId<AlignmentCsvMetaData<T>>?>,
    ) {
        val sql = """
            insert into layout.initial_segment_metadata(alignment_id, segment_index, metadata_id)
            values (:alignment_id, :segment_index, :metadata_id)
        """.trimIndent()
        val params = segmentMetadataIds.mapIndexedNotNull { index, metadataId ->
            metadataId?.let { mdId -> mapOf(
                "alignment_id" to alignmentId.intValue,
                "segment_index" to index,
                "metadata_id" to mdId.intValue,
            ) }
        }.toTypedArray()
        jdbcTemplate.batchUpdate(sql, params)
    }
}
