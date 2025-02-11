import { MapLayer } from 'map/layers/utils/layer-model';
import { Point as OlPoint } from 'ol/geom';
import Feature from 'ol/Feature';
import Style from 'ol/style/Style';
import { Circle, Fill, Stroke } from 'ol/style';
import mapStyles from 'map/map.module.scss';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { getSwitches } from 'track-layout/layout-switch-api';
import { LayoutSwitch, splitPointsAreSame } from 'track-layout/track-layout-model';
import { MapLayerName } from 'map/map-model';
import { draftLayoutContext, LayoutContext } from 'common/common-model';
import { filterNotEmpty } from 'utils/array-utils';

const splitPointStyle = new Style({
    image: new Circle({
        radius: 8,
        fill: new Fill({ color: mapStyles.splitPointCircleColor }),
        stroke: new Stroke({ color: mapStyles.alignmentBadgeWhite, width: 2 }),
    }),
    zIndex: 2,
});

const splitPointDisabledStyle = new Style({
    image: new Circle({
        radius: 8,
        fill: new Fill({ color: mapStyles.splitPointDisabledCircleColor }),
        stroke: new Stroke({ color: mapStyles.alignmentBadgeWhite, width: 2 }),
    }),
    zIndex: 2,
});

const splitPointFocusedStyle = new Style({
    image: new Circle({
        radius: 10,
        fill: new Fill({ color: mapStyles.splitPointCircleColor }),
        stroke: new Stroke({ color: mapStyles.alignmentBadgeWhite, width: 2 }),
    }),
    zIndex: 3,
});

const deletedSplitPointStyle = new Style({
    image: new Circle({
        radius: 8,
        fill: new Fill({ color: mapStyles.splitPointDeletedCircleColor }),
        stroke: new Stroke({ color: mapStyles.alignmentBadgeWhite, width: 2 }),
    }),
});

const layerName: MapLayerName = 'location-track-split-location-layer';

export const createLocationTrackSplitLocationLayer = (
    existingOlLayer: GeoviiteMapLayer<OlPoint> | undefined,
    layoutContext: LayoutContext,
    splittingState: SplittingState | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer => {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise: Promise<LayoutSwitch[]> = splittingState
        ? getSwitches(
              splittingState.splits
                  .map((split) =>
                      split.splitPoint.type === 'SWITCH_SPLIT_POINT'
                          ? split.splitPoint.switchId
                          : undefined,
                  )
                  .filter(filterNotEmpty),
              draftLayoutContext(layoutContext),
          )
        : Promise.resolve([]);

    const createFeatures = (switches: LayoutSwitch[]) => {
        if (splittingState) {
            const splitPoints = [
                splittingState.startSplitPoint,
                ...splittingState.splits.map((split) => split.splitPoint),
                splittingState.endSplitPoint,
            ];

            return splitPoints.map((splitPoint) => {
                const isDeletedSwitch =
                    switches.find(
                        (sw) =>
                            splitPoint.type === 'SWITCH_SPLIT_POINT' &&
                            sw.id === splitPoint.switchId,
                    )?.stateCategory === 'NOT_EXISTING';

                const feature = new Feature({
                    geometry: new OlPoint(pointToCoords(splitPoint.location)),
                });

                const isHighlighted =
                    splittingState.highlightedSplitPoint &&
                    splitPointsAreSame(splittingState.highlightedSplitPoint, splitPoint);

                const getSelectedStyle = () => {
                    if (splittingState?.disabled) {
                        return splitPointDisabledStyle;
                    }
                    if (isDeletedSwitch) {
                        return deletedSplitPointStyle;
                    } else if (isHighlighted) {
                        return splitPointFocusedStyle;
                    } else {
                        return splitPointStyle;
                    }
                };

                feature.setStyle(getSelectedStyle);
                return feature;
            });
        } else {
            return [];
        }
    };

    loadLayerData(source, isLatest, onLoadingData, dataPromise, createFeatures);

    return { name: layerName, layer: layer };
};
