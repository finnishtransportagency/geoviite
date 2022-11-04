package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.ratko.model.*
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.GeocodingService
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles


@ActiveProfiles("dev", "test")
@SpringBootTest
@ConditionalOnBean(RatkoClient::class)
@Disabled
class RatkoClientIT @Autowired constructor(
    private val geocodingService: GeocodingService,
    private val ratkoClient: RatkoClient,
    private val switchDao: LayoutSwitchDao,
    private val locationTrackService: LocationTrackService,
    private val switchLibraryService: SwitchLibraryService,
): ITTestBase() {

    private val switchOwners = switchLibraryService.getSwitchOwners()

    @Test
    fun shouldFetchRatkoSwitchByExternalId() {
        val switchId = IntId<TrackLayoutSwitch>(2)
        val layoutSwitch = getGeoviiteLayoutSwitchById(switchId)
        val switchExternalId = layoutSwitch.externalId
        checkNotNull(switchExternalId)
        ratkoClient.getSwitchAsset(RatkoOid(switchExternalId.stringValue))
    }

    @Test
    fun shouldUpdateTrackAlignmentDescription() {
        val (track, _) = getUpdateLayoutAlignment()
        val ratkoAlignment =
            convertToRatkoLocationTrack(
                track.copy(description = FreeText("Päivitetty kuvaus")),
                getTrackNumber().externalId,
                duplicateOfOid = null,
            )
        ratkoClient.updateLocationTrackProperties(ratkoAlignment)
        val locationTrack = ratkoClient.getLocationTrack(RatkoOid("1.2.246.578.3.10002.189390"))

        assertNotNull(locationTrack)

        val updatedDescription = locationTrack!!.description
        assertEquals("Päivitetty kuvaus", updatedDescription)
    }

    @Test
    fun shouldAddNewTrackAlignment() {
        val track = locationTrackService.getOrThrow(PublishType.OFFICIAL, IntId(997))
        val addresses = geocodingService.getAddressPoints(track, PublishType.OFFICIAL)
            ?: throw IllegalStateException("Cannot calculate addresses for location track ${track.id}")
        val ratkoNodes = convertToRatkoNodeCollection(addresses)
        val ratkoAlignment =
            convertToRatkoLocationTrack(track, getTrackNumber().externalId, ratkoNodes, duplicateOfOid = null)
        val trackId = ratkoClient.newLocationTrack(ratkoAlignment)

        assertNotNull(trackId)
        val locationTrack = ratkoClient.getLocationTrack(trackId!!)
        assertNotNull(locationTrack)
        assertEquals(ratkoAlignment.name, locationTrack!!.name)
    }

    @Test
    fun shouldUpdateRatkoSwitchProperties() {
        val layoutSwitch = switchDao.fetch(
            switchDao.fetchOfficialVersion(IntId(2))!!
        )
        checkNotNull(layoutSwitch.externalId)
        val basicUpdateSwitch = createRatkoBasicUpdateSwitchById()

        ratkoClient.updateAssetProperties(RatkoOid(basicUpdateSwitch.id!!), basicUpdateSwitch.properties)
    }

/*    @Test
    fun shouldCreateRatkoSwitchStructureFromJointConnections() {
        val jointConnections = listOf(
            TrackLayoutSwitchJointConnection(
                number = JointNumber(1),
                location = Point(x = 428323.7263818098, y = 7207564.834561082),
                accurateMatches = listOf(
                    TrackLayoutSwitchJointMatch(
                        locationTrackId = IntId(1121),
                        atStart = false,
                        atEnd = false
                    ),
                    TrackLayoutSwitchJointMatch(
                        locationTrackId = IntId(2063),
                        atStart = false,
                        atEnd = false
                    )
                )
            ),
            TrackLayoutSwitchJointConnection(
                number = JointNumber(2),
                location = Point(x = 428310.2297223286, y = 7207589.697646854),
                accurateMatches = listOf(
                    TrackLayoutSwitchJointMatch(
                        locationTrackId = IntId(1121),
                        atStart = false,
                        atEnd = false
                    ),
                )
            ),
            TrackLayoutSwitchJointConnection(
                number = JointNumber(3),
                location = Point(x = 428311.95123278955, y = 7207590.512230684),
                accurateMatches = listOf(
                    TrackLayoutSwitchJointMatch(
                        locationTrackId = IntId(2063),
                        atStart = false,
                        atEnd = false
                    ),
                )
            ),
            TrackLayoutSwitchJointConnection(
                number = JointNumber(5),
                location = Point(x = 428318.4445096105, y = 7207574.565994358),
                accurateMatches = listOf()
            )
        )
        val locationTrackJoints = convertJointConnectionToRatkoStructure(jointConnections, SwitchBaseType.YV)

        val expected = listOf(
            LocationTrackJoint(
                locationTrackId = IntId(1121),
                jointPoints = listOf(
                    JointPoint(
                        joint = RatkoNodeType.JOINT_A,
                        point = Point(x = 428323.7263818098, y = 7207564.834561082),
                        kmM = null
                    ),
                    JointPoint(
                        joint = RatkoNodeType.JOINT_B,
                        point = Point(x = 428310.2297223286, y = 7207589.697646854),
                        kmM = null
                    )
                )
            ),
            LocationTrackJoint(
                locationTrackId = IntId(2063),
                jointPoints = listOf(
                    JointPoint(
                        joint = RatkoNodeType.JOINT_A,
                        point = Point(x = 428323.7263818098, y = 7207564.834561082),
                        kmM = null
                    ),
                    JointPoint(
                        joint = RatkoNodeType.JOINT_C,
                        point = Point(x = 428311.95123278955, y = 7207590.512230684),
                        kmM = null
                    )
                )
            )
        )
        assertEquals(expected, locationTrackJoints)
    }*/

    /* @Test
     fun shouldCreateRatkoLocations() {
         val geoviiteLocationsData =
             listOf(
                 GeoviiteNodeCollection(
                     joints = listOf(RatkoNodeType.JOINT_A, RatkoNodeType.JOINT_B),
                     kmMs = listOf(TrackMeter("0749", 489.50855873174186), TrackMeter("0749", 522.3472844541685)),
                     locationTrackOid = Oid("1.2.246.578.3.10002.190776"),
                     routeNumberOid = Oid("1.2.246.578.3.10001.189249"),
                     locations = null,
                 ),
                 GeoviiteNodeCollection(
                     joints = listOf(RatkoNodeType.JOINT_A, RatkoNodeType.JOINT_C),
                     kmMs = listOf(TrackMeter("0749", 489.50855873174186), TrackMeter("0749", 522.2973224902706)),
                     locationTrackOid = Oid("1.2.246.578.3.10002.191942"),
                     routeNumberOid = Oid("1.2.246.578.3.10001.189249"),
                     locations = null,
                 )
             )
         val actualLocationsData = createRatkoPutSwitchLocations(geoviiteLocationsData)
         val expected = listOf(
             RatkoAssetLocation(
                 nodecollection = RatkoNodes(
                     nodes = listOf(
                         RatkoNode(
                             nodeType = RatkoNodeType.JOINT_A,
                             point = RatkoPoint(
                                 trackMeter = RatkoTrackMeter(KmNumber("0749"), 489.509),
                                 geometry = null,
                                 state = null,
                                 rowMetadata = null,
                                 locationtrack = RatkoOid("1.2.246.578.3.10002.190776"),
                                 routenumber = RatkoOid("1.2.246.578.3.10001.189249")
                             )
                         ),
                         RatkoNode(
                             nodeType = RatkoNodeType.JOINT_B,
                             point = RatkoPoint(
                                 trackMeter = RatkoTrackMeter(KmNumber("0749"), 522.347),
                                 geometry = null,
                                 state = null,
                                 rowMetadata = null,
                                 locationtrack = RatkoOid("1.2.246.578.3.10002.190776"),
                                 routenumber = RatkoOid("1.2.246.578.3.10001.189249")
                             )
                         )
                     ),
                     type = RatkoNodesType.JOINTS
                 ),
                 priority = 1,
                 accuracyType = RatkoLocationAccuracy.GEOMETRY_CALCULATED,
             ),
             RatkoAssetLocation(
                 nodecollection = RatkoNodes(
                     nodes = listOf(
                         RatkoNode(
                             nodeType = RatkoNodeType.JOINT_A,
                             point = RatkoPoint(
                                 trackMeter = RatkoTrackMeter(KmNumber("0749"), 0489.509),
                                 geometry = null, state = null, rowMetadata = null,
                                 locationtrack = RatkoOid("1.2.246.578.3.10002.191942"),
                                 routenumber = RatkoOid("1.2.246.578.3.10001.189249")
                             )
                         ),
                         RatkoNode(
                             nodeType = RatkoNodeType.JOINT_C,
                             point = RatkoPoint(
                                 trackMeter = RatkoTrackMeter(KmNumber("0749"), 522.297),
                                 geometry = null,
                                 state = null,
                                 rowMetadata = null,
                                 locationtrack = RatkoOid("1.2.246.578.3.10002.191942"),
                                 routenumber = RatkoOid("1.2.246.578.3.10001.189249")
                             )
                         )
                     ), type = RatkoNodesType.JOINTS
                 ), priority = 2,
                 accuracyType = RatkoLocationAccuracy.GEOMETRY_CALCULATED
             )
         )
         assertRatkoNodeCollectionsEquals(expected, actual = actualLocationsData)
     }*/


    /* @Test
     fun shouldAddRatkoSwitchPointsToTrackLayout(){
         // update points only found on this list
         val locationTrackOidList: List<Oid<LocationTrack>> = listOf(Oid("1.2.246.578.3.10002.190776"))

         val switchId = IntId<TrackLayoutSwitch>(2)
         val layoutSwitch = getGeoviiteLayoutSwitchById(switchId)
         val jointConnections: List<TrackLayoutSwitchJointConnection> = switchDao.fetchSwitchJointConnections(
             PublishType.OFFICIAL,
             switchId
         )
         val switchStructure = structureDao.fetchSwitchStructures()
             .find { switchStructure -> switchStructure.id.intValue == layoutSwitch.switchStructureId.intValue }
         val switchType: SwitchBaseType? = switchStructure?.baseType
         checkNotNull(switchType)
         val locationTrackJoints: List<LocationTrackJoint> =
             convertJointConnectionToRatkoStructure(jointConnections, switchType)
         val geoviiteNodeCollections = createGeoviiteNodeCollections(locationTrackJoints)
         val trackLocationsPointLists = createRatkoSwitchPointAdditionList(geoviiteNodeCollections, locationTrackOidList)

         for(trackLocationPointList in trackLocationsPointLists){

             val points = trackLocationPointList.points
             val oid = trackLocationPointList.locationTrackOid
             ratkoClient.updateSwitchPointsToLocationTrack(oid, points)
         }

     }*/

    /*@Test
    fun shouldReplaceWithRatkoValuesWhenLocationTrackNotInIdList() {

        val switchId = IntId<TrackLayoutSwitch>(2)
        val layoutSwitch = getGeoviiteLayoutSwitchById(switchId)
        val switchExternalId = layoutSwitch.externalId
        checkNotNull(switchExternalId)
        val ratkoSwitch = ratkoClient.getSwitch(RatkoOid(switchExternalId.stringValue))
        val switchStructure = structureDao.fetchSwitchStructures()
            .find { switchStructure -> switchStructure.id.intValue == layoutSwitch.switchStructureId.intValue }
        val switchType: SwitchBaseType? = switchStructure?.baseType

        checkNotNull(switchType)
        val jointConnections: List<TrackLayoutSwitchJointConnection> = switchDao.fetchSwitchJointConnections(
            PublishType.OFFICIAL,
            switchId
        )
        val locationTrackJoints: List<LocationTrackJoint> =
            convertJointConnectionToRatkoStructure(jointConnections, switchType)
        val geoviiteNodeCollections: List<GeoviiteNodeCollection> = locationTrackJoints.map { locationTrackJoint ->

            val locationTrack: LocationTrack = locationTrackDao.fetch(RowVersion(locationTrackJoint.locationTrackId, 1))
            val locationTrackOid = locationTrack.externalId
            val trackLayoutTrackNumber = layoutTrackNumberService.get(
                PublishType.OFFICIAL,
                locationTrack.trackNumberId as IntId<TrackLayoutTrackNumber>
            )
            val trackNumberOid = trackLayoutTrackNumber.externalId
            // IMPORTANT! kmM values must be rounded to 3 decimals
            val kmMsGeoviite: List<TrackMeter> = createRatkoKmMValues(
                locationTrackJoint.jointPoints,
                locationTrack.trackNumberId as IntId<TrackLayoutTrackNumber>
            )

            val joints: List<RatkoNodeType> = locationTrackJoint.jointPoints.map { jointPoint -> jointPoint.joint }

            checkNotNull(locationTrackOid)
            checkNotNull(trackNumberOid)

            GeoviiteNodeCollection(
                joints = joints,
                kmMs = kmMsGeoviite,
                locationTrackOid = locationTrackOid,
                routeNumberOid = trackNumberOid,
                locations = null,
            )
        }

        val locationTrackOidList: List<Oid<LocationTrack>> = listOf(Oid("1.2.246.578.3.10002.190776"))
        val ratkoLocations = ratkoSwitch.locations
        checkNotNull(ratkoLocations)
        val merged = mergeGeoviiteRatkoLocationsBylocationTrackIds(
            locationTrackOidList,
            geoviiteNodeCollections,
            ratkoLocations
        )


        assertNotEquals(geoviiteNodeCollections, merged)
    }
*/
    /* @Test
     fun shouldUpdateRatkoSwitchLocations() {
         val locationTrackOidList: List<Oid<LocationTrack>> = listOf(Oid("1.2.246.578.3.10002.190776"))
         val switchId = IntId<TrackLayoutSwitch>(2)
         val layoutSwitch = getGeoviiteLayoutSwitchById(switchId)

         val switchStructure = structureDao.fetchSwitchStructures()
             .find { switchStructure -> switchStructure.id.intValue == layoutSwitch.switchStructureId.intValue }
         val switchType: SwitchBaseType? = switchStructure?.baseType

         checkNotNull(switchType)
         val jointConnections: List<TrackLayoutSwitchJointConnection> = switchDao.fetchSwitchJointConnections(
             PublishType.OFFICIAL,
             switchId
         )

         val locationTrackJoints: List<LocationTrackJoint> =
             convertJointConnectionToRatkoStructure(jointConnections, switchType)
         val geoviiteNodeCollections = createGeoviiteNodeCollections(locationTrackJoints)
         val trackLocationsPointLists = createRatkoSwitchPointAdditionList(geoviiteNodeCollections, locationTrackOidList)
         for (trackLocationPointList in trackLocationsPointLists) {

             val points = trackLocationPointList.points
             val oid = trackLocationPointList.locationTrackOid
             ratkoClient.updateSwitchPointsToLocationTrack(oid, points)
         }
         createRatkoSwitchPointAdditionList(geoviiteNodeCollections, locationTrackOidList)
         val switchExternalId = layoutSwitch.externalId
         checkNotNull(switchExternalId)
         val ratkoSwitch = ratkoClient.getSwitch(RatkoOid(switchExternalId.stringValue))
         val ratkoLocations = ratkoSwitch.locations
         checkNotNull(ratkoLocations)
         val mergedLocations = mergeGeoviiteRatkoLocationsBylocationTrackIds(
             locationTrackOidList,
             geoviiteNodeCollections,
             ratkoLocations
         )

         val geoviiteLocations = createRatkoPutSwitchLocations(mergedLocations)
         val ratkoBasicUpdateSwitch = createRatkoBasicUpdateSwitchById(switchId)
         ratkoClient.updateSwitchLocationsGeometry(ratkoBasicUpdateSwitch, geoviiteLocations )
     }
 */
    /*@Test
    fun shouldUpdateRatkoSwitchGeoms() {
        val layoutSwitch: TrackLayoutSwitch = switchDao.fetchSwitch(
            switchDao.fetchOfficialSwitchVersion(IntId(2))
        )
        val switchStructure = structureDao.fetchSwitchStructures()
            .find { switchStructure -> switchStructure.id.intValue == layoutSwitch.switchStructureId.intValue }
        val switchType: SwitchBaseType? = switchStructure?.baseType

        checkNotNull(switchType)

        val ratkoSwitch = ratkoClient.getSwitch(RatkoOid(layoutSwitch.externalId!!.stringValue))
        val ratkoState = ratkoSwitch.state ?: RatkoAssetState.BUILT
        val basicUpdateSwitch = createRatkoPutAsset(ratkoSwitch, layoutSwitch, ratkoState)

        val geoms = createRatkoPutAssetGeoms(layoutSwitch.joints, switchType)

        ratkoClient.updateSwitchJointsGeometry(basicUpdateSwitch, geoms.filterNotNull())
    }*/

/*    private fun assertRatkoNodeCollectionsEquals(expected: List<RatkoAssetLocation>,actual: List<RatkoAssetLocation>) {
        assertEquals(expected.size, actual.size)
        assertEquals(expected[0].priority, actual[0].priority)
        val expectedNodeCollection = expected[0].nodecollection
        val actualNodeCollection = actual[0].nodecollection
        val expectedAccuracyType = expected[0].accuracyType
        val actualAccuracyType = actual[0].accuracyType
        assertEquals(expectedNodeCollection.nodes[0].nodeType, actualNodeCollection.nodes[0].nodeType)
        assertEquals(expectedNodeCollection.nodes[0].point.kmM.pointType, actualNodeCollection.nodes[0].point.kmM.pointType)
        assertEquals(expectedNodeCollection.nodes[0].point.kmM.kmNumber, actualNodeCollection.nodes[0].point.kmM.kmNumber)
        assertEquals(expectedNodeCollection.nodes[0].point.kmM.toString(), actualNodeCollection.nodes[0].point.kmM.toString())
        assertEquals(expectedNodeCollection.nodes[0].point.state, actualNodeCollection.nodes[0].point.state)
        assertEquals(expectedNodeCollection.nodes[0].point.routenumber, actualNodeCollection.nodes[0].point.routenumber)
        assertEquals(expectedNodeCollection.nodes[0].point.locationtrack, actualNodeCollection.nodes[0].point.locationtrack)
        assertEquals(expectedNodeCollection.nodes[0].point.trackMeter.pointType, actualNodeCollection.nodes[0].point.trackMeter.pointType)
        assertEquals(expectedNodeCollection.nodes[0].point.trackMeter.kmNumber, actualNodeCollection.nodes[0].point.trackMeter.kmNumber)
        assertEquals(expectedNodeCollection.nodes[0].point.trackMeter.toString(), actualNodeCollection.nodes[0].point.trackMeter.toString())
        assertRatkoNodeCollectionsGeometryEquals(expectedNodeCollection.nodes[0].point.geometry, actualNodeCollection.nodes[0].point.geometry)
        assertEquals(expectedAccuracyType.value, actualAccuracyType.value)
        assertEquals(expectedAccuracyType.name, actualAccuracyType.name)
    }*/

/*    private fun assertRatkoNodeCollectionsGeometryEquals(expectedGeometry: RatkoGeometry?, actualGeometry: RatkoGeometry?){
        if(expectedGeometry != null && actualGeometry != null) {
            assertEquals(expectedGeometry.crs.properties.name, actualGeometry.crs.properties.name)
            assertEquals(expectedGeometry.type.name, actualGeometry.type.name)
            assertEquals(expectedGeometry.coordinates[0], actualGeometry.coordinates[0])
            assertEquals(expectedGeometry.coordinates[1], actualGeometry.coordinates[1])
        }
    }*/


    /*fun createGeoviiteNodeCollections(locationTrackJoints: List<LocationTrackJoint>): List<GeoviiteNodeCollection> {

        return locationTrackJoints.map { locationTrackJoint ->
            val locationTrack: LocationTrack = locationTrackDao.fetch(RowVersion(locationTrackJoint.locationTrackId, 1))
            val locationTrackOid = locationTrack.externalId
            val trackLayoutTrackNumber = layoutTrackNumberService.get(
                PublishType.OFFICIAL,
                locationTrack.trackNumberId as IntId<TrackLayoutTrackNumber>
            )
            val trackNumberOid = trackLayoutTrackNumber.externalId
            val kmMsGeoviite = createRatkoKmMValues(
                locationTrackJoint.jointPoints,
                locationTrack.trackNumberId as IntId<TrackLayoutTrackNumber>
            )
            val joints = locationTrackJoint.jointPoints.map { jointPoint -> jointPoint.joint }
            val locations = locationTrackJoint.jointPoints.mapNotNull { jointPoint -> jointPoint.point }

            checkNotNull(locationTrackOid)
            checkNotNull(trackNumberOid)

            GeoviiteNodeCollection(
                joints = joints,
                locations = locations,
                kmMs = kmMsGeoviite,
                locationTrackOid = locationTrackOid,
                routeNumberOid = trackNumberOid,
            )
        }
    }*/

    fun getGeoviiteLayoutSwitchById(value: IntId<TrackLayoutSwitch> = IntId(2)): TrackLayoutSwitch {

        return switchDao.fetch(
            switchDao.fetchOfficialVersion(value)!!
        )
    }

    fun createRatkoBasicUpdateSwitchById(value: IntId<TrackLayoutSwitch> = IntId(2)): RatkoSwitchAsset {
        val layoutSwitch = switchDao.fetch(
            switchDao.fetchOfficialVersion(value)!!
        )
        val switchStructure = switchLibraryService.getSwitchStructure(layoutSwitch.switchStructureId)
        val ratkoSwitch = ratkoClient.getSwitchAsset(RatkoOid(layoutSwitch.externalId!!.stringValue))
        return convertToRatkoSwitch(layoutSwitch, switchStructure, switchOwners.firstOrNull(), ratkoSwitch)
    }

    /*fun createRatkoKmMValues(
        jointPoints: List<JointPoint>,
        trackNumberId: DomainId<TrackLayoutTrackNumber>
    ): List<TrackMeter> {
        return jointPoints.mapNotNull { jointPoint ->
            val point = jointPoint.point
            checkNotNull(point)
            geocodingService.getTrackAddress(
                trackNumberId as IntId<TrackLayoutTrackNumber>,
                point, PublishType.OFFICIAL
            )
        }.map { trackMeter ->
            TrackMeter(trackMeter.kmNumber, roundTo3Decimals(trackMeter.meters).toDouble())
        }
    }*/

    @Test
    fun shouldGetExternalIdForNewLayoutTrack() {
        val externalId = ratkoClient.getNewLocationTrackOid()
        assertNotNull(externalId)
    }

    @Test
    fun shouldGetExternalIdForNewTrackNumber() {
        val externalId = ratkoClient.getNewRouteNumberOid()
        assertNotNull(externalId)
    }

}
