package fi.fta.geoviite.infra.tracklayout.graph

/** Level of detail at which the track layout topology is described. */
enum class TopologyDetailLevel {
    NANO,
    MICRO,
}

// TODO: GVT-3743, GVT-3744: the actual topology domain model (nodes, edges and their track references) is defined here
class Topology
