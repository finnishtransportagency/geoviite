import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.api.tracklayout.v1.ExtTrackLayoutTestApiService
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtTestTrackLayoutV1IT
@Autowired
constructor(
    mockMvc: MockMvc,
    private val locationTrackService: LocationTrackService,
    private val extTestDataService: ExtApiTestDataServiceV1,
    private val publicationService: PublicationService,
) : DBTestBase() {

    private val api = ExtTrackLayoutTestApiService(mockMvc)

    private val errorTests =
        listOf(
            ::setupValidLocationTrack to api.locationTracks::getWithExpectedError,
            ::setupValidLocationTrack to api.locationTracks::getGeometryWithExpectedError,
            ::setupValidTrackNumber to api.trackNumbers::getWithExpectedError,
            ::setupValidTrackNumber to api.trackNumbers::getGeometryWithExpectedError,
            ::setupValidTrackNumber to api.trackNumberKms::getWithExpectedError,
            ::setupValidSwitch to api.switch::getWithExpectedError,
        )

    private val collectionErrorTests =
        listOf(
            api.locationTrackCollection::getWithExpectedError,
            api.trackNumberCollection::getWithExpectedError,
            api.trackNumberKmsCollection::getWithExpectedError,
            api.switchCollection::getWithExpectedError,
        )

    private val modificationErrorTests =
        listOf(
            ::setupValidLocationTrack to api.locationTracks::getModifiedWithExpectedError,
            ::setupValidTrackNumber to api.trackNumbers::getModifiedWithExpectedError,
            ::setupValidSwitch to api.switch::getModifiedWithExpectedError,
        )

    private val collectionModificationErrorTests =
        listOf(
            api.locationTrackCollection::getModifiedWithExpectedError,
            api.trackNumberCollection::getModifiedWithExpectedError,
            api.switchCollection::getModifiedWithExpectedError,
        )

    private val geometryErrorTests =
        listOf(
            ::setupValidLocationTrack to api.locationTracks::getGeometryWithExpectedError,
            ::setupValidTrackNumber to api.trackNumbers::getGeometryWithExpectedError,
        )

    private val noContentTests =
        listOf(
            ::setupValidLocationTrack to api.locationTracks::getWithEmptyBody,
            ::setupValidLocationTrack to api.locationTracks::getGeometryWithEmptyBody,
            ::setupValidTrackNumber to api.trackNumbers::getWithEmptyBody,
            ::setupValidTrackNumber to api.trackNumbers::getGeometryWithEmptyBody,
            ::setupValidSwitch to api.switch::getWithEmptyBody,
        )

    private val collectionNoContentTests =
        listOf(
            ::setupValidLocationTrackCollection to api.locationTrackCollection::getModifiedWithEmptyBody,
            ::setupValidTrackNumberCollection to api.trackNumberCollection::getModifiedWithEmptyBody,
            ::setupValidSwitchCollection to api.switchCollection::getModifiedWithEmptyBody,
        )

    private val noContentModificationTests =
        listOf(
            ::setupValidLocationTrack to api.locationTracks::getModifiedWithEmptyBody,
            ::setupValidTrackNumber to api.trackNumbers::getModifiedWithEmptyBody,
            ::setupValidSwitch to api.switch::getModifiedWithEmptyBody,
        )

    private val modificationSuccessTests =
        listOf(
            ::setupValidLocationTrack to api.locationTracks::getModified,
            ::setupValidTrackNumber to api.trackNumbers::getModified,
            ::setupValidSwitch to api.switch::getModified,
        )

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Ext api asset endpoints should return HTTP 400 if the OID is invalid format`() {
        val invalidOid = "asd"
        val expectedStatus = HttpStatus.BAD_REQUEST
        val validButEmptyPublication = extTestDataService.publishInMain()

        errorTests.forEach { (_, apiCall) -> apiCall(invalidOid, emptyArray(), expectedStatus) }
        modificationErrorTests.forEach { (_, apiCall) ->
            apiCall(invalidOid, arrayOf("alkuversio" to validButEmptyPublication.uuid.toString()), expectedStatus)
        }
    }

    @Test
    fun `Ext api asset endpoints should return HTTP 404 if the OID is not found`() {
        val expectedStatus = HttpStatus.NOT_FOUND
        val nonExistingOid = someOid<Nothing>().toString()
        val validButEmptyPublication = extTestDataService.publishInMain()

        errorTests.forEach { (_, apiCall) -> apiCall(nonExistingOid, emptyArray(), expectedStatus) }
        modificationErrorTests.forEach { (_, apiCall) ->
            apiCall(nonExistingOid, arrayOf("alkuversio" to validButEmptyPublication.uuid.toString()), expectedStatus)
        }
    }

    @Test
    fun `Ext api asset endpoints should return HTTP 400 if the track layout version is invalid format`() {
        val invalidTrackLayoutVersion = "asd"
        val expectedStatus = HttpStatus.BAD_REQUEST

        errorTests
            .map { (oidSetup, apiCall) -> oidSetup().toString() to apiCall }
            .forEach { (oid, apiCall) ->
                val response = apiCall(oid, arrayOf("rataverkon_versio" to invalidTrackLayoutVersion), expectedStatus)
            }
    }

    @Test
    fun `Ext api asset endpoints return HTTP 404 if the track layout version is not found`() {
        val validButNonExistingUuid = "00000000-0000-0000-0000-000000000000"
        val expectedStatus = HttpStatus.NOT_FOUND

        errorTests
            .map { (oidSetup, apiCall) -> oidSetup().toString() to apiCall }
            .forEach { (oid, apiCall) ->
                apiCall(oid, arrayOf("rataverkon_versio" to validButNonExistingUuid), expectedStatus)
            }
    }

    @Test
    fun `Ext api asset modification endpoints should return HTTP 400 if either the start or end track layout version is invalid format`() {
        val invalidTrackLayoutVersion = "asd"
        val validButNonExistingUuid = "00000000-0000-0000-0000-000000000000"
        val expectedStatus = HttpStatus.BAD_REQUEST

        modificationErrorTests
            .map { (oidSetup, apiCall) -> oidSetup().toString() to apiCall }
            .forEach { (oid, apiCall) ->
                apiCall(oid, arrayOf("alkuversio" to invalidTrackLayoutVersion), expectedStatus)
                apiCall(
                    oid,
                    arrayOf("alkuversio" to validButNonExistingUuid, "loppuversio" to invalidTrackLayoutVersion),
                    expectedStatus,
                )
            }
    }

    @Test
    fun `Ext api asset modification endpoints should return HTTP 400 if the start and end track layout versions are in the incorrect order`() {
        val startPublication = extTestDataService.publishInMain()
        val endPublication = extTestDataService.publishInMain()
        val expectedStatus = HttpStatus.BAD_REQUEST

        modificationErrorTests
            .map { (oidSetup, apiCall) -> oidSetup().toString() to apiCall }
            .forEach { (oid, apiCall) ->
                apiCall(
                    oid,
                    arrayOf(
                        "alkuversio" to endPublication.uuid.toString(),
                        "loppuversio" to startPublication.uuid.toString(),
                    ),
                    expectedStatus,
                )
            }
    }

    @Test
    fun `Ext api asset modification endpoints should return HTTP 404 if either the start or end track layout version is not found`() {
        val emptyButExistingPublication = extTestDataService.publishInMain()
        val validButNonExistingUuid = "00000000-0000-0000-0000-000000000000"

        val expectedStatus = HttpStatus.NOT_FOUND

        modificationErrorTests
            .map { (oidSetup, apiCall) -> oidSetup().toString() to apiCall }
            .forEach { (oid, apiCall) ->
                apiCall(oid, arrayOf("alkuversio" to validButNonExistingUuid), expectedStatus)
                apiCall(
                    oid,
                    arrayOf(
                        "alkuversio" to emptyButExistingPublication.uuid.toString(),
                        "loppuversio" to validButNonExistingUuid,
                    ),
                    expectedStatus,
                )
            }
    }

    @Test
    fun `Ext api asset endpoints should return HTTP 204 when the asset does not exist in the specified track layout version`() {
        val expectedStatus = HttpStatus.NO_CONTENT
        val validButEmptyPublication = extTestDataService.publishInMain()

        noContentTests
            .map { (oidSetup, apiCall) -> oidSetup() to apiCall }
            .forEach { (oid, apiCall) ->
                apiCall(oid, arrayOf("rataverkon_versio" to validButEmptyPublication.uuid.toString()), expectedStatus)
            }
    }

    @Test
    fun `Ext api asset modification endpoints should return HTTP 204 when the asset has no modifications`() {
        val expectedStatus = HttpStatus.NO_CONTENT

        noContentModificationTests
            .map { (oidSetup, apiCall) -> oidSetup() to apiCall }
            .forEach { (oid, apiCall) ->
                val newestPublication =
                    publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, publicationUuid = null)

                apiCall(oid, arrayOf("alkuversio" to newestPublication.uuid.toString()), expectedStatus)
            }
    }

    @Test
    fun `Ext api asset modification endpoints should return HTTP 204 when the asset does not exist between track layout versions`() {
        val expectedStatus = HttpStatus.NO_CONTENT
        val firstPublication = extTestDataService.publishInMain()
        val secondPublication = extTestDataService.publishInMain()

        noContentModificationTests
            .map { (oidSetup, apiCall) -> oidSetup() to apiCall }
            .forEach { (oid, apiCall) ->
                // oidSetup call has added the asset and created a new publication: the asset exists, but not within the
                // publications before it.
                apiCall(
                    oid,
                    arrayOf(
                        "alkuversio" to firstPublication.uuid.toString(),
                        "loppuversio" to secondPublication.uuid.toString(),
                    ),
                    expectedStatus,
                )
            }
    }

    @Test
    fun `Ext api asset modification endpoints should return HTTP 200 when the asset has been created after the start track layout version`() {
        val validButEmptyPublication = extTestDataService.publishInMain()

        modificationSuccessTests
            .map { (oidSetup, apiCall) -> oidSetup() to apiCall }
            .forEach { (oid, apiCall) ->
                apiCall(oid, arrayOf("alkuversio" to validButEmptyPublication.uuid.toString()))
            }
    }

    @Test
    fun `Ext api asset collection modification endpoints should return HTTP 404 if either start or end track layout version is not found`() {
        val nonExistingTrackLayoutVersion = "00000000-0000-0000-0000-000000000000"
        val emptyButRealPublication = extTestDataService.publishInMain()

        collectionModificationErrorTests.forEach { apiCall ->
            apiCall(arrayOf("alkuversio" to nonExistingTrackLayoutVersion), HttpStatus.NOT_FOUND)

            apiCall(
                arrayOf("alkuversio" to nonExistingTrackLayoutVersion, "loppuversio" to nonExistingTrackLayoutVersion),
                HttpStatus.NOT_FOUND,
            )

            apiCall(
                arrayOf(
                    "alkuversio" to emptyButRealPublication.uuid.toString(),
                    "loppuversio" to nonExistingTrackLayoutVersion,
                ),
                HttpStatus.NOT_FOUND,
            )

            apiCall(
                arrayOf("alkuversio" to nonExistingTrackLayoutVersion, "loppuversio" to nonExistingTrackLayoutVersion),
                HttpStatus.NOT_FOUND,
            )
        }
    }

    @Test
    fun `Ext api asset collection modification endpoints should return HTTP 400 if the start or end track layout versions is in invalid format`() {
        val invalidTrackLayoutVersion = "asd"
        val emptyButRealPublication = extTestDataService.publishInMain()

        collectionModificationErrorTests.forEach { apiCall ->
            apiCall(arrayOf("alkuversio" to invalidTrackLayoutVersion), HttpStatus.BAD_REQUEST)

            apiCall(
                arrayOf("alkuversio" to invalidTrackLayoutVersion, "loppuversio" to invalidTrackLayoutVersion),
                HttpStatus.BAD_REQUEST,
            )

            apiCall(
                arrayOf(
                    "alkuversio" to emptyButRealPublication.uuid.toString(),
                    "loppuversio" to invalidTrackLayoutVersion,
                ),
                HttpStatus.BAD_REQUEST,
            )

            apiCall(
                arrayOf("alkuversio" to invalidTrackLayoutVersion, "loppuversio" to invalidTrackLayoutVersion),
                HttpStatus.BAD_REQUEST,
            )
        }
    }

    @Test
    fun `Ext api asset collection modification endpoints should return HTTP 400 if the start and end track layout versions are in the incorrect order`() {
        val startPublication = extTestDataService.publishInMain()
        val endPublication = extTestDataService.publishInMain()

        collectionModificationErrorTests.forEach { apiCall ->
            apiCall(
                arrayOf(
                    "alkuversio" to endPublication.uuid.toString(),
                    "loppuversio" to startPublication.uuid.toString(),
                ),
                HttpStatus.BAD_REQUEST,
            )
        }
    }

    @Test
    fun `Ext api asset collection endpoints should return HTTP 400 if the track layout version is invalid format`() {
        collectionErrorTests.forEach { apiCall ->
            apiCall(arrayOf("rataverkon_versio" to "asd"), HttpStatus.BAD_REQUEST)
        }
    }

    @Test
    fun `Ext api asset collection endpoints should return HTTP 404 if the track layout version is not found`() {
        extTestDataService.publishInMain() // Purposefully a publication so that it does not answer based on this

        collectionErrorTests.forEach { apiCall ->
            apiCall(arrayOf("rataverkon_versio" to "00000000-0000-0000-0000-000000000000"), HttpStatus.NOT_FOUND)
        }
    }

    @Test
    fun `Ext api asset collection modification endpoints should return HTTP 204 when there are no modifications`() {
        val lastContentPublication = collectionNoContentTests.map { (setupCollection, _) -> setupCollection() }.last()
        val lastButEmptyPublication = extTestDataService.publishInMain()

        collectionNoContentTests.forEach { (_, apiCall) ->
            apiCall(
                arrayOf(
                    "alkuversio" to lastContentPublication.uuid.toString(),
                    "loppuversio" to lastContentPublication.uuid.toString(), // Purposefully the same exact version
                ),
                HttpStatus.NO_CONTENT,
            )

            apiCall(
                arrayOf(
                    "alkuversio" to lastContentPublication.uuid.toString()
                    // This should implicitly use the last available track layout version which does not contain any
                    // meaningful changes
                ),
                HttpStatus.NO_CONTENT,
            )

            apiCall(
                arrayOf(
                    "alkuversio" to lastContentPublication.uuid.toString(),
                    "loppuversio" to lastButEmptyPublication.uuid.toString(),
                ),
                HttpStatus.NO_CONTENT,
            )
        }
    }

    @Test
    fun `Ext api asset geometry endpoints should return HTTP 400 if resolution is wrong`() {

        geometryErrorTests
            .map { (oidSetup, apiCall) -> oidSetup() to apiCall }
            .forEach { (oid, apiCall) ->
                apiCall(oid.toString(), arrayOf("osoitepistevali" to "asd"), HttpStatus.BAD_REQUEST)
                apiCall(oid.toString(), arrayOf("osoitepistevali" to "-1"), HttpStatus.BAD_REQUEST)
                apiCall(oid.toString(), arrayOf("osoitepistevali" to "0.250"), HttpStatus.BAD_REQUEST)
                apiCall(oid.toString(), arrayOf("osoitepistevali" to "0"), HttpStatus.BAD_REQUEST)
                apiCall(oid.toString(), arrayOf("osoitepistevali" to "10.0"), HttpStatus.BAD_REQUEST)
                apiCall(oid.toString(), arrayOf("osoitepistevali" to "1337"), HttpStatus.BAD_REQUEST)
            }
    }

    private fun setupValidTrackNumber(): Oid<LayoutTrackNumber> {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val (trackNumberId, referenceLineId, oid) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

        extTestDataService.publishInMain(trackNumbers = listOf(trackNumberId), referenceLines = listOf(referenceLineId))

        return oid
    }

    private fun setupValidTrackNumberCollection(): Publication {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val trackNumbersAndReferenceLines =
            listOf(1, 2, 3).map { _ ->
                val (trackNumberId, referenceLineId, _) =
                    extTestDataService.insertTrackNumberAndReferenceLineWithOid(
                        mainDraftContext,
                        segments = listOf(segment),
                    )

                trackNumberId to referenceLineId
            }

        return extTestDataService.publishInMain(
            trackNumbers = trackNumbersAndReferenceLines.map { it.first },
            referenceLines = trackNumbersAndReferenceLines.map { it.second },
        )
    }

    private fun setupValidLocationTrack(): Oid<LocationTrack> {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

        val trackId = mainDraftContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment)).id

        val oid =
            someOid<LocationTrack>().also { oid ->
                locationTrackService.insertExternalId(LayoutBranch.main, trackId, oid)
            }

        extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = listOf(trackId),
        )

        return oid
    }

    private fun setupValidLocationTrackCollection(): Publication {
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))

        val (trackNumberId, referenceLineId, _) =
            extTestDataService.insertTrackNumberAndReferenceLineWithOid(mainDraftContext, segments = listOf(segment))

        val tracks =
            listOf(1, 2, 3).map { _ ->
                val trackId = mainDraftContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId, segment)).id
                locationTrackService.insertExternalId(LayoutBranch.main, trackId, someOid())

                trackId
            }

        return extTestDataService.publishInMain(
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(referenceLineId),
            locationTracks = tracks,
        )
    }

    private fun setupValidSwitch(): Oid<LayoutSwitch> {
        val structure = switchStructureYV60_300_1_9()
        val joint1 = switchJoint(1, Point(0.0, 0.0))
        val joint2 = switchJoint(2, Point(100.0, 0.0))
        val ids = extTestDataService.insertSwitchAndTracks(mainDraftContext, listOf(joint1 to joint2), structure)

        extTestDataService.publishInMain(listOf(ids))

        return ids.switch.oid
    }

    private fun setupValidSwitchCollection(): Publication {
        val structure = switchStructureYV60_300_1_9()

        val ids =
            listOf(1, 2, 3).map { idx ->
                val joint1 = switchJoint(1, Point(idx * 0.0, 0.0))
                val joint2 = switchJoint(2, Point(idx * 100.0, 0.0))
                extTestDataService.insertSwitchAndTracks(mainDraftContext, listOf(joint1 to joint2), structure)
            }

        return extTestDataService.publishInMain(ids)
    }
}
