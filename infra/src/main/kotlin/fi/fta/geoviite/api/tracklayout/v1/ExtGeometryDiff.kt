package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.tracklayout.AnyM
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_COORDINATE_DELTA

fun isGeometryChanged(start: IPoint, end: IPoint, oldGeometry: IAlignment<*>?, newGeometry: IAlignment<*>?): Boolean =
    when {
        oldGeometry == null && newGeometry == null -> false
        oldGeometry == null || newGeometry == null -> true
        else -> {
            val oldGeoms = getGeometryIdsBetweenPoints(oldGeometry, start, end)
            val newGeoms = getGeometryIdsBetweenPoints(newGeometry, start, end)
            oldGeoms != newGeoms
        }
    }

private fun getGeometryIdsBetweenPoints(alignment: IAlignment<*>, start: IPoint, end: IPoint) =
    alignment.getSegmentsBetween(start, end).map { it.geometry.id as IntId }

fun <M : AnyM<M>> createModifiedCenterLineIntervals(
    oldPoints: AlignmentAddresses<M>?,
    newPoints: AlignmentAddresses<M>?,
    coordinateSystem: Srid,
    isGeometryChange: (start: IPoint, end: IPoint) -> Boolean,
): List<ExtModifiedCenterLineTrackIntervalV1> =
    when {
        oldPoints == null && newPoints == null -> error("Must have some points to compare")
        oldPoints == null -> {
            // Full new interval was added
            listOf(
                ExtModifiedCenterLineTrackIntervalV1(
                    startAddress = newPoints!!.startPoint.address.formatFixedDecimals(3),
                    endAddress = newPoints.endPoint.address.formatFixedDecimals(3),
                    changeType = ExtGeometryChangeTypeV1.GEOMETRY,
                    addressPoints =
                        newPoints.allPoints.map { addressPoint -> toExtAddressPoint(addressPoint, coordinateSystem) },
                )
            )
        }
        newPoints == null -> {
            // All points removed on the original interval
            listOf(
                ExtModifiedCenterLineTrackIntervalV1(
                    startAddress = oldPoints.startPoint.address.formatFixedDecimals(3),
                    endAddress = oldPoints.endPoint.address.formatFixedDecimals(3),
                    changeType = ExtGeometryChangeTypeV1.GEOMETRY,
                    addressPoints = emptyList(),
                )
            )
        }
        else -> {
            val changedIntervals = mutableListOf<ExtModifiedCenterLineTrackIntervalV1>()
            val rangeBuilder = RangeBuilder<M>(coordinateSystem)
            var oldIndex = 0
            var newIndex = 0
            while (oldIndex < oldPoints.allPoints.lastIndex || newIndex < newPoints.allPoints.lastIndex) {
                val old = oldPoints.allPoints.getOrNull(oldIndex)
                val new = newPoints.allPoints.getOrNull(newIndex)
                when {
                    old == null && new == null -> {
                        error("Loop failed to end despite both lists being fully processed")
                    }
                    old == null -> { // Reached the end of the old points -> rest of new different
                        rangeBuilder.extendWithPoints(newPoints.allPoints.subList(newIndex, newPoints.allPoints.size))
                        newIndex = newPoints.allPoints.size
                    }
                    new == null -> { // Reached the end of the new points -> rest of old different
                        rangeBuilder.extendAsEmpty(old.address)
                        rangeBuilder.extendAsEmpty(oldPoints.allPoints.last().address)
                        oldIndex = oldPoints.allPoints.size
                    }
                    else ->
                        when {
                            old.address < new.address -> {
                                rangeBuilder.extendAsEmpty(old.address)
                                oldIndex++
                            }
                            old.address > new.address -> {
                                rangeBuilder.extendWithPoint(new)
                                newIndex++
                            }
                            else -> {
                                if (old.point.isSame(new.point, LAYOUT_COORDINATE_DELTA)) {
                                    rangeBuilder.buildAndReset(isGeometryChange)?.let(changedIntervals::add)
                                } else {
                                    rangeBuilder.extendWithPoint(new)
                                }
                                oldIndex++
                                newIndex++
                            }
                        }
                }
            }
            // Finish range if one still exists
            rangeBuilder.buildAndReset(isGeometryChange)?.let(changedIntervals::add)
            changedIntervals
        }
    }

private class RangeBuilder<M : AnyM<M>>(val coordinateSystem: Srid) {
    private var start: TrackMeter? = null
    private var end: TrackMeter? = null
    private val points = mutableListOf<AddressPoint<M>>()

    fun extendAsEmpty(address: TrackMeter) {
        if (start == null) start = address
        end = address
    }

    fun extendWithPoint(addressPoint: AddressPoint<M>) {
        if (start == null) start = addressPoint.address
        end = addressPoint.address
        points.add(addressPoint)
    }

    fun extendWithPoints(addressPoints: List<AddressPoint<M>>) {
        if (addressPoints.isNotEmpty()) {
            if (start == null) start = addressPoints.first().address
            end = addressPoints.last().address
            points.addAll(addressPoints)
        }
    }

    fun buildAndReset(
        isGeometryChange: (start: IPoint, end: IPoint) -> Boolean
    ): ExtModifiedCenterLineTrackIntervalV1? {
        val startAddress = start?.formatFixedDecimals(3)
        val endAddress = end?.formatFixedDecimals(3)
        val addressPoints = points.map { addressPoint -> toExtAddressPoint(addressPoint, coordinateSystem) }
        val changeType =
            if (points.isEmpty() || isGeometryChange(points.first().point, points.last().point)) {
                ExtGeometryChangeTypeV1.GEOMETRY
            } else {
                ExtGeometryChangeTypeV1.ADDRESSING
            }
        reset()
        return if (startAddress != null && endAddress != null) {
            ExtModifiedCenterLineTrackIntervalV1(startAddress, endAddress, changeType, addressPoints)
        } else {
            null
        }
    }

    private fun reset() {
        start = null
        end = null
        points.clear()
    }
}

fun toExtInterval(addressPoints: AlignmentAddresses<*>, coordinateSystem: Srid): ExtCenterLineTrackIntervalV1 =
    ExtCenterLineTrackIntervalV1(
        startAddress = addressPoints.startPoint.address.formatFixedDecimals(3),
        endAddress = addressPoints.endPoint.address.formatFixedDecimals(3),
        addressPoints =
            addressPoints.allPoints.map { addressPoint -> toExtAddressPoint(addressPoint, coordinateSystem) },
    )
