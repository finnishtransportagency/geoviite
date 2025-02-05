import { MapLayerName, MapTile } from 'map/map-model';
import { LayoutContext } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayer } from 'map/layers/utils/layer-model';
import { HIGHLIGHTS_SHOW } from 'map/layers/utils/layer-visibility-limits';
import {
    AlignmentDataHolder,
    AlignmentHeader,
    getSelectedLocationTrackMapAlignmentByTiles,
    getSelectedReferenceLineMapAlignmentByTiles,
} from 'track-layout/layout-map-api';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { LineString } from 'ol/geom';
import Feature from 'ol/Feature';
import { blueHighlightStyle } from 'map/layers/utils/highlight-layer-utils';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { getPartialPolyLine } from 'utils/math-utils';
import { LayoutTrackNumberId } from 'track-layout/track-layout-model';

const isReferenceLine = (header: AlignmentHeader, trackNumberId: LayoutTrackNumberId) =>
    header.trackNumberId === trackNumberId && header.alignmentType === 'REFERENCE_LINE';

function createFeatures(
    alignments: AlignmentDataHolder[],
    hoveredOverItem: HighlightedAlignment | undefined,
): Feature<LineString>[] {
    return alignments
        .filter(
            (alignment) =>
                hoveredOverItem !== undefined &&
                (hoveredOverItem.type === 'REFERENCE_LINE'
                    ? isReferenceLine(alignment.header, hoveredOverItem.id)
                    : alignment.header.id === hoveredOverItem.id),
        )
        .flatMap(({ points }) => {
            const polyline =
                hoveredOverItem &&
                hoveredOverItem.startM !== undefined &&
                hoveredOverItem.endM !== undefined
                    ? getPartialPolyLine(points, hoveredOverItem?.startM, hoveredOverItem?.endM)
                    : [];
            const lineString = new LineString(polyline);
            const feature = new Feature({ geometry: lineString });

            feature.setStyle(blueHighlightStyle);

            return feature;
        });
}

const layerName: MapLayerName = 'plan-section-highlight-layer';

function getAlignments(
    mapTiles: MapTile[],
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    resolution: number,
    hoveredOverItem: HighlightedAlignment | undefined,
): Promise<AlignmentDataHolder[]> {
    if (resolution > HIGHLIGHTS_SHOW || !hoveredOverItem) {
        return Promise.resolve([]);
    } else if (hoveredOverItem.type === 'REFERENCE_LINE') {
        return getSelectedReferenceLineMapAlignmentByTiles(
            changeTimes,
            mapTiles,
            layoutContext,
            hoveredOverItem.id,
        );
    } else {
        return getSelectedLocationTrackMapAlignmentByTiles(
            changeTimes,
            mapTiles,
            layoutContext,
            hoveredOverItem.id,
        );
    }
}

export function createPlanSectionHighlightLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString> | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    resolution: number,
    hoveredOverItem: HighlightedAlignment | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise: Promise<AlignmentDataHolder[]> = getAlignments(
        mapTiles,
        layoutContext,
        changeTimes,
        resolution,
        hoveredOverItem,
    );

    const createOlFeatures = (alignments: AlignmentDataHolder[]) =>
        createFeatures(alignments, hoveredOverItem);

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createOlFeatures);

    return { name: layerName, layer: layer };
}
