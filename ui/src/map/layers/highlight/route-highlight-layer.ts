import Feature from 'ol/Feature';
import mapStyles from 'map/map.module.scss';
import { LineString, Point as OlPoint } from 'ol/geom';
import { MapLayerName, MapTile } from 'map/map-model';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { MapLayer } from 'map/layers/utils/layer-model';
import { ChangeTimes } from 'common/common-slice';
import { LayoutContext } from 'common/common-model';
import { RouteResult } from 'track-layout/layout-routing-api';
import { getLocationTrackMapAlignmentsByTiles } from 'track-layout/layout-map-api';
import { getPartialPolyLine } from 'utils/math-utils';
import { Stroke, Style } from 'ol/style';
import { filterNotEmpty } from 'utils/array-utils';

type RouteSection = {
    polyline: number[][];
};

const routeSectionStyle = new Style({
    stroke: new Stroke({
        color: mapStyles['routeColor'],
        width: 6,
    }),
});

async function getRouteSections(
    mapTiles: MapTile[],
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    routeResult: RouteResult,
): Promise<RouteSection[]> {
    const alignmentDataHolders = await getLocationTrackMapAlignmentsByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
    );

    const trackAlignmentMap = new Map();
    alignmentDataHolders.forEach((alignmentDataHolder) => {
        trackAlignmentMap.set(alignmentDataHolder.header.id, alignmentDataHolder.points);
    });

    const routeSections = routeResult.route.sections
        .map((section) => {
            const points = trackAlignmentMap.get(section.trackId);
            if (points && points.length > 1) {
                const polyline = getPartialPolyLine(points, section.mRange.min, section.mRange.max);

                if (polyline.length > 1) {
                    return {
                        polyline: polyline,
                    };
                }
            }
            return undefined;
        })
        .filter(filterNotEmpty);

    return routeSections;
}

function createRouteSectionFeatures(routeSections: RouteSection[]): Feature<LineString>[] {
    return routeSections.map((routeSection) => {
        const lineString = new LineString(routeSection.polyline);
        const feature = new Feature({ geometry: lineString });
        feature.setStyle(routeSectionStyle);
        return feature;
    });
}

const layerName: MapLayerName = 'route-highlight-layer';

export function createRouteHighlightLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString | OlPoint> | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    routeResult: RouteResult | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const getRouteSectionsPromise: Promise<RouteSection[]> = routeResult
        ? getRouteSections(mapTiles, layoutContext, changeTimes, routeResult)
        : Promise.resolve([]);

    loadLayerData(
        source,
        isLatest,
        onLoadingData,
        getRouteSectionsPromise,
        createRouteSectionFeatures,
    );

    return { name: layerName, layer: layer };
}
