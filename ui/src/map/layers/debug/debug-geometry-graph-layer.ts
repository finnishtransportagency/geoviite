import Feature from 'ol/Feature';
import { LineString, Point as OlPoint } from 'ol/geom';
import { filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import { Circle, Stroke, Style } from 'ol/style';
import { LayerItemSearchResult, MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import {
    createLayer,
    findMatchingEntities,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { MapLayerName, MapTile } from 'map/map-model';
import {
    LayoutEdge,
    LayoutGraph,
    LayoutGraphLevel,
    LayoutNode,
    LayoutNodeId,
} from 'track-layout/track-layout-model';
import { getLayoutGraph } from 'track-layout/track-layout-api';
import { LayoutContext } from 'common/common-model';
import { GEOMETRY_GRAPH, SWITCH_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { clamp } from 'utils/math-utils';
import { brand } from 'common/brand';
import { Rectangle } from 'model/geometry';

const colors = [
    'rgb(0, 0, 0)', // hsl(0, 0%, 0%)
    'rgb(0, 0, 255)', // hsl(240, 100%, 50%)
    'rgb(255, 0, 255)', // hsl(300, 100%, 50%)
    'rgb(255, 255, 0)', // hsl(0, 100%, 50%)
    'rgb(0, 255, 0)', // hsl(60, 100%, 50%)
    'rgb(0, 255, 255)', // hsl(120, 100%, 50%)
    'rgb(0, 0, 255)', // hsl(180, 100%, 50%)
];

function createNodeFeatures(
    points: Map<LayoutNodeId, LayoutNode>,
    resolution: number,
): Feature<OlPoint>[] {
    const features: Feature<OlPoint>[] = [];
    points.forEach((node) => {
        const feature = new Feature({
            node: node,
            geometry: new OlPoint(pointToCoords(node.location)),
        });
        const mainNode =
            node.type !== 'SWITCH' || node.switches.some((nsw) => nsw.jointRole === 'MAIN');

        const color = mainNode ? 'red' : colors[1];
        const width = mainNode ? 4 : 2;
        const size = mainNode
            ? resolution <= SWITCH_SHOW
                ? 10
                : 5
            : resolution <= SWITCH_SHOW
              ? 6
              : 3;

        feature.setStyle(
            new Style({
                image: new Circle({
                    radius: size,
                    stroke: new Stroke({ color, width }),
                }),
            }),
        );

        features.push(feature);
    });
    return features;
}

function createEdgeFeatures(
    edges: LayoutEdge[],
    nodes: Map<LayoutNodeId, LayoutNode>,
): Feature<LineString>[] {
    return edges
        .flatMap((edge) => {
            const startNode = nodes.get(edge.startNode);
            const endNode = nodes.get(edge.endNode);

            if (startNode && endNode) {
                const feature = new Feature({
                    edge: edge,
                    geometry: new LineString([
                        pointToCoords(startNode.location),
                        pointToCoords(endNode.location),
                    ]),
                });
                const color = colors[edge.tracks.length % colors.length];
                const width = clamp(edge.tracks.length * 2, 2, 10);

                feature.setStyle(
                    new Style({
                        stroke: new Stroke({
                            color,
                            width,
                        }),
                    }),
                );

                return feature;
            } else return undefined;
        })
        .filter(filterNotEmpty);
}

const layerName: MapLayerName = 'debug-geometry-graph-layer';

export function createDebugGeometryGraphLayer(
    existingOlLayer: GeoviiteMapLayer<LineString | OlPoint> | undefined,
    onLoadingData: (loading: boolean) => void,
    layoutContext: LayoutContext,
    mapTiles: MapTile[],
    resolution: number,
    detailLevel: LayoutGraphLevel,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const updateLayerFunc = (graphTiles: LayoutGraph[]) => {
        const nodes = new Map<LayoutNodeId, LayoutNode>();

        graphTiles.forEach((tile) => {
            Object.entries(tile.nodes).forEach(([id, node]) => {
                nodes.set(brand(id), node);
            });
        });
        const flattenedEdges = graphTiles
            .flatMap((graph) => Object.values(graph.edges))
            .filter(filterUniqueById((e: LayoutEdge) => e.id));
        return [
            ...createNodeFeatures(nodes, resolution),
            ...createEdgeFeatures(flattenedEdges, nodes),
        ];
    };

    const tiledPromises =
        resolution <= GEOMETRY_GRAPH
            ? mapTiles.map((tile) => getLayoutGraph(layoutContext, tile.area, detailLevel))
            : [
                  Promise.resolve<LayoutGraph>({
                      nodes: {},
                      edges: {},
                      context: layoutContext,
                      detailLevel: detailLevel,
                  }),
              ];

    loadLayerData(source, isLatest, onLoadingData, Promise.all(tiledPromises), updateLayerFunc);

    return {
        name: layerName,
        layer: layer,
        searchItems: (hitArea: Rectangle, options: SearchItemsOptions): LayerItemSearchResult => {
            const nodes = findMatchingEntities<LayoutNode>(hitArea, source, 'node', options);
            const edges = findMatchingEntities<LayoutEdge>(hitArea, source, 'edge', options);
            // console.log(nodes, edges);
            return {
                locationTracks: edges.flatMap((e) => e.tracks),
                switches: nodes.flatMap((n) => n.switches.map((s) => s.id)),
                // locationTracks: findMatchingAlignments(hitArea, source, options).map(({ header }) =>
                //     brand(header.id),
                // ),
                // switches:
            };
        },
    };
}
