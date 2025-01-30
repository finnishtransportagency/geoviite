package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.geography.ETRS89_TM35FIN_SRID
import fi.fta.geoviite.infra.logging.Loggable
import java.time.Instant

val LAYOUT_SRID = ETRS89_TM35FIN_SRID

enum class LayoutState(val category: LayoutStateCategory) {
    IN_USE(LayoutStateCategory.EXISTING),
    NOT_IN_USE(LayoutStateCategory.EXISTING),
    DELETED(LayoutStateCategory.NOT_EXISTING);

    fun isLinkable() = this == IN_USE || this == NOT_IN_USE

    fun isRemoved() = this == DELETED
}

enum class LayoutStateCategory {
    EXISTING,
    NOT_EXISTING;

    fun isLinkable() = this == EXISTING

    fun isRemoved() = this == NOT_EXISTING
}

sealed class LayoutAsset<T : LayoutAsset<T>>(contextData: LayoutContextData<T>) :
    LayoutContextAware<T> by contextData, Loggable {
    @get:JsonIgnore abstract val contextData: LayoutContextData<T>

    val hasOfficial: Boolean
        get() = contextData.hasOfficial

    abstract fun withContext(contextData: LayoutContextData<T>): T

    @get:JsonIgnore
    val versionOrThrow
        get() =
            requireNotNull(version) {
                "Expected object to be stored in DB and hence have a version: object=${this.toLog()}"
            }
}

sealed class PolyLineLayoutAsset<T : PolyLineLayoutAsset<T>>(contextData: LayoutContextData<T>) :
    LayoutAsset<T>(contextData) {}

data class LayoutAssetChangeInfo(val created: Instant, val changed: Instant?)
