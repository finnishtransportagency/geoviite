package fi.fta.geoviite.infra.ui.testdata

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber

class SearchTestData {
    companion object {

        val BASE_POINT_ESPOO = Point(EspooTestData.BASE_POINT_X, EspooTestData.BASE_POINT_Y)
        val BASE_POINT_HELSINKI =  Point(HelsinkiTestData.HKI_BASE_POINT_X, HelsinkiTestData.HKI_BASE_POINT_Y)


        fun createLocationTrackHkiA(trackNumberId: IntId<LayoutTrackNumber>) =
            locationTrack(
                name = "LT-A HKI",
                trackNumber = trackNumberId,
                basePoint = BASE_POINT_HELSINKI + Point(x = 40.0, y = -13.0),
                incrementPoints =  listOf(Point(x = 20.0, y = 2.0), Point(x = 20.0, y = 2.0)),
                description = "location track near: Helsinki"

            )

        fun createLocationTrackHkiB(trackNumberId: IntId<LayoutTrackNumber>) =
            locationTrack(
                name = "LT-B HKI",
                trackNumber = trackNumberId,
                basePoint = BASE_POINT_HELSINKI + Point(x = 50.0, y = -23.0),
                incrementPoints =  listOf(Point(x = 20.0, y = 2.0), Point(x = 20.0, y = 2.0)),
                description = "location track near: Helsinki"

            )

        fun createLocationTrackEspA(trackNumberId: IntId<LayoutTrackNumber>) =
            locationTrack(
                name = "LT-A ESP 1",
                trackNumber = trackNumberId,
                basePoint = BASE_POINT_ESPOO + Point(x = 50.0, y = -23.0),
                incrementPoints =  listOf(Point(x = 20.0, y = 2.0), Point(x = 20.0, y = 2.0)),
                description = "location track near: Espoo"

            )

        fun createLocationTrackEspB(trackNumberId: IntId<LayoutTrackNumber>) =
            locationTrack(
                name = "LT-B ESP 2",
                trackNumber = trackNumberId,
                basePoint = BASE_POINT_ESPOO + Point(x = 40.0, y = -20.0),
                incrementPoints =  listOf(Point(x = 20.0, y = 2.0), Point(x = 20.0, y = 2.0)),
                description = "location track near: Espoo"

            )

    }
}
