import Feature from 'ol/Feature';
import { LineString, Point as OlPoint } from 'ol/geom';
import { filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import { Circle, Stroke, Style } from 'ol/style';
import { MapLayer } from 'map/layers/utils/layer-model';
import { createLayer, loadLayerData, pointToCoords } from 'map/layers/utils/layer-utils';
import VectorLayer from 'ol/layer/Vector';
import { MapLayerName, MapTile } from 'map/map-model';
import { LayoutEdge, LayoutGraph, LayoutNode } from 'track-layout/track-layout-model';
import { getLayoutGraph } from 'track-layout/track-layout-api';
import { LayoutContext } from 'common/common-model';
import {
    ALL_ALIGNMENTS,
    SWITCH_LARGE_SYMBOLS,
    SWITCH_SHOW,
} from 'map/layers/utils/layer-visibility-limits';
import { clamp } from 'utils/math-utils';

const colors = [
    'rgb(0, 0, 0)', // hsl(0, 0%, 0%)
    'rgb(0, 0, 255)', // hsl(240, 100%, 50%)
    'rgb(255, 0, 255)', // hsl(300, 100%, 50%)
    'rgb(255, 255, 0)', // hsl(0, 100%, 50%)
    'rgb(0, 255, 0)', // hsl(60, 100%, 50%)
    'rgb(0, 255, 255)', // hsl(120, 100%, 50%)
    'rgb(0, 0, 255)', // hsl(180, 100%, 50%)
];

function createNodeFeatures(points: LayoutNode[], resolution: number): Feature<OlPoint>[] {
    return points
        .flatMap((node) => {
            const feature = new Feature({
                geometry: new OlPoint(pointToCoords(node.location)),
            });

            const color = 'blue';
            const size =
                resolution <= SWITCH_LARGE_SYMBOLS ? 20 : resolution <= SWITCH_SHOW ? 10 : 5;

            feature.setStyle(
                new Style({
                    image: new Circle({
                        radius: size,
                        stroke: new Stroke({ color, width: 2 }),
                    }),
                }),
            );

            return feature;
        })
        .filter(filterNotEmpty);
}

function createEdgeFeatures(edges: LayoutEdge[], nodes: LayoutNode[]): Feature<LineString>[] {
    return edges
        .flatMap((edge) => {
            const startNode = nodes.find((n) => n.id === edge.startNode);
            const endNode = nodes.find((n) => n.id === edge.endNode);

            if (startNode && endNode) {
                const feature = new Feature({
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
    existingOlLayer: VectorLayer<Feature<LineString | OlPoint>> | undefined,
    onLoadingData: (loading: boolean) => void,
    layoutContext: LayoutContext,
    mapTiles: MapTile[],
    resolution: number,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const updateLayerFunc = (graphTiles: LayoutGraph[]) => {
        const flattenedNodes = graphTiles
            .flatMap((graph) => Object.values(graph.nodes))
            .filter(filterUniqueById((n: LayoutNode) => n.id));
        const flattenedEdges = graphTiles
            .flatMap((graph) => Object.values(graph.edges))
            .filter(filterUniqueById((e: LayoutEdge) => e.id));
        return [
            ...createNodeFeatures(flattenedNodes, resolution),
            ...createEdgeFeatures(flattenedEdges, flattenedNodes),
        ];
    };

    const tiledPromises =
        resolution <= ALL_ALIGNMENTS
            ? mapTiles.map((tile) => getLayoutGraph(layoutContext, tile.area))
            : [Promise.resolve<LayoutGraph>({ nodes: {}, edges: {}, context: layoutContext })];

    loadLayerData(source, isLatest, onLoadingData, Promise.all(tiledPromises), updateLayerFunc);

    return { name: layerName, layer: layer };
}
