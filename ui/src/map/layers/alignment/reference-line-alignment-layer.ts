import { MapLayerName, MapTile, OptionalShownItems } from 'map/map-model';
import { LineString, Point as OlPoint } from 'ol/geom';
import { Selection } from 'selection/selection-model';
import { LayoutContext } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayer, SearchItemsOptions } from 'map/layers/utils/layer-model';
import { createLayer, GeoviiteMapLayer, loadLayerData } from 'map/layers/utils/layer-utils';
import { deduplicate, filterNotEmpty } from 'utils/array-utils';
import {
    getReferenceLineMapAlignmentsByTiles,
    ReferenceLineAlignmentDataHolder,
} from 'track-layout/layout-map-api';
import {
    createAlignmentFeatures,
    findMatchingAlignments,
    NORMAL_ALIGNMENT_OPACITY,
    OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING,
} from 'map/layers/utils/alignment-layer-utils';
import { ReferenceLineId } from 'track-layout/track-layout-model';
import { Rectangle } from 'model/geometry';
import { Stroke, Style } from 'ol/style';
import mapStyles from 'map/map.module.scss';

let shownReferenceLinesCompare: string;

const highlightedReferenceLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.selectedAlignmentLine,
        width: 3,
    }),
    zIndex: 1,
});

const referenceLineStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentLine,
        width: 3,
    }),
    zIndex: 0,
});

const layerName: MapLayerName = 'reference-line-alignment-layer';

export function createReferenceLineAlignmentLayer(
    mapTiles: MapTile[],
    existingOlLayer: GeoviiteMapLayer<LineString | OlPoint> | undefined,
    selection: Selection,
    isSplitting: boolean,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    onViewContentChanged: (items: OptionalShownItems) => void,
    onLoadingData: (loading: boolean) => void,
): MapLayer {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    layer.setOpacity(
        isSplitting ? OTHER_ALIGNMENTS_OPACITY_WHILE_SPLITTING : NORMAL_ALIGNMENT_OPACITY,
    );

    function updateShownReferenceLines(referenceLineIds: ReferenceLineId[]) {
        const compare = referenceLineIds.sort().join();

        if (compare !== shownReferenceLinesCompare) {
            shownReferenceLinesCompare = compare;
            onViewContentChanged({ referenceLines: referenceLineIds });
        }
    }

    const dataPromise: Promise<ReferenceLineAlignmentDataHolder[]> =
        getReferenceLineMapAlignmentsByTiles(changeTimes, mapTiles, layoutContext);

    const createFeatures = (referenceLines: ReferenceLineAlignmentDataHolder[]) =>
        createAlignmentFeatures(
            referenceLines,
            selection,
            false,
            referenceLineStyle,
            highlightedReferenceLineStyle,
        );

    const onLoadingChange = (
        loading: boolean,
        referenceLines: ReferenceLineAlignmentDataHolder[] | undefined,
    ) => {
        if (!loading) {
            updateShownReferenceLines(referenceLines?.map(({ header }) => header.id) ?? []);
        }
        onLoadingData(loading);
    };

    loadLayerData(source, isLatest, onLoadingChange, dataPromise, createFeatures);

    const searchItems = (hitArea: Rectangle, options: SearchItemsOptions) => {
        const referenceLines = findMatchingAlignments(hitArea, source, options);
        const trackNumberIds = deduplicate(
            referenceLines.map((rl) => rl.header.trackNumberId).filter(filterNotEmpty),
        );

        return {
            referenceLines: referenceLines.map((r) => r.header.id),
            trackNumbers: trackNumberIds,
        };
    };

    return {
        name: layerName,
        layer: layer,
        searchItems: searchItems,
        onRemove: () => updateShownReferenceLines([]),
    };
}
