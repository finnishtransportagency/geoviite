package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.util.logger

fun publicationsAreTheSame(publicationUuid: Uuid<Publication>): Nothing? {
    logger.info(
        "There cannot be any differences if the requested publications are the same, publicationUuid=${publicationUuid}"
    )
    return null
}

inline fun <reified T : LayoutAsset<T>> layoutAssetVersionsAreTheSame(
    assetId: IntId<T>,
    publications: PublicationComparison,
): Nothing? {
    logger.info(
        "The versions used for comparing ${T::class.java}: assetId=${assetId}, fromPublication: ${publications.to.id} -> toPublication: ${publications.from.id}"
    )
    return null
}

inline fun <reified T> layoutAssetCollectionWasUnmodified(
    publications: PublicationComparison,
): Nothing? {
    logger.info(
        "The ${T::class.java} collection was unmodified, fromPublication: ${publications.from.id} -> toPublication: ${publications.to.id}"
    )
    return null
}
