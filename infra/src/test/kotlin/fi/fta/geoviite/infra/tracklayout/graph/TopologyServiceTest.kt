package fi.fta.geoviite.infra.tracklayout.graph

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.dataImport.switchStructures
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TopologyServiceTest {

    @Test
    fun `Switch transitions match every switch library alignment`() {
        switchStructures.forEach { structure ->
            structure.endJointNumbers.forEach { joint ->
                val connections = endpointConnections(structure, joint)
                val expected = expectedTransitions(connections)
                val actual = createSwitchTransitions(connections)

                assertEquals(
                    actual,
                    actual.distinct(),
                    "Duplicate topology transitions for switch type ${structure.type}, joint $joint",
                )
                assertEquals(
                    expected,
                    actual.toSet(),
                    "Unexpected topology transitions for switch type ${structure.type}, joint $joint",
                )
            }
        }
    }

    private fun endpointConnections(
        structure: SwitchStructureData,
        joint: JointNumber,
    ): List<EndpointConnection> {
        val key = SwitchJointKey(SWITCH_ID, joint)
        val externalConnection =
            EndpointConnection(
                edgeId = uuid("external-$joint"),
                endpoint = EdgeEndpoint.END,
                side = SwitchSide.External(key),
            )
        val internalConnections =
            structure.alignments.mapIndexedNotNull { index, alignment ->
                when (joint) {
                    alignment.jointNumbers.first() ->
                        EndpointConnection(
                            edgeId = uuid("alignment-$index"),
                            endpoint = EdgeEndpoint.START,
                            side = SwitchSide.Internal(key),
                        )
                    alignment.jointNumbers.last() ->
                        EndpointConnection(
                            edgeId = uuid("alignment-$index"),
                            endpoint = EdgeEndpoint.END,
                            side = SwitchSide.Internal(key),
                        )
                    else -> null
                }
            }
        return listOf(externalConnection) + internalConnections
    }

    private fun expectedTransitions(connections: List<EndpointConnection>): Set<GeneratedTransition> {
        val external = connections.single { connection -> connection.side is SwitchSide.External }
        return connections
            .filter { connection -> connection.side is SwitchSide.Internal }
            .flatMap { internal ->
                listOf(
                    GeneratedTransition(
                        incomingEdge = external.edgeId,
                        incomingDirection = external.incomingDirection,
                        outgoingEdge = internal.edgeId,
                        outgoingDirection = internal.outgoingDirection,
                    ),
                    GeneratedTransition(
                        incomingEdge = internal.edgeId,
                        incomingDirection = internal.incomingDirection,
                        outgoingEdge = external.edgeId,
                        outgoingDirection = external.outgoingDirection,
                    ),
                )
            }
            .toSet()
    }

    private fun uuid(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))

    companion object {
        private val SWITCH_ID = IntId<LayoutSwitch>(1)
    }
}
