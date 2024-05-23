import { MapLayer } from 'map/layers/utils/layer-model';
import { Point as OlPoint } from 'ol/geom';
import VectorLayer from 'ol/layer/Vector';
import Feature from 'ol/Feature';
import Style from 'ol/style/Style';
import { Circle, Fill, Stroke } from 'ol/style';
import mapStyles from 'map/map.module.scss';
import { createLayer, loadLayerData, pointToCoords } from 'map/layers/utils/layer-utils';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { getSwitches } from 'track-layout/layout-switch-api';
import { LayoutSwitch, LayoutSwitchId } from 'track-layout/track-layout-model';
import { Point } from 'model/geometry';
import { MapLayerName } from 'map/map-model';
import { draftLayoutContext, LayoutContext } from 'common/common-model';

const splitPointStyle = new Style({
    image: new Circle({
        radius: 8,
        fill: new Fill({ color: mapStyles.splitPointCircleColor }),
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

type SwitchIdAndLocation = {
    switchId: LayoutSwitchId | undefined;
    location: Point;
};

const layerName: MapLayerName = 'location-track-split-location-layer';

export const createLocationTrackSplitLocationLayer = (
    existingOlLayer: VectorLayer<Feature<OlPoint>> | undefined,
    layoutContext: LayoutContext,
    splittingState: SplittingState | undefined,
    onLoadingData: (loading: boolean) => void,
): MapLayer => {
    const { layer, source, isLatest } = createLayer(layerName, existingOlLayer);

    const dataPromise: Promise<LayoutSwitch[]> = splittingState
        ? getSwitches(
              splittingState.splits.map((sw) => sw.switch.switchId),
              draftLayoutContext(layoutContext),
          )
        : Promise.resolve([]);

    const createFeatures = (switches: LayoutSwitch[]) => {
        if (splittingState) {
            const firstAndLast: SwitchIdAndLocation[] = [
                { location: splittingState.firstSplit.location, switchId: undefined },
                { location: splittingState.endLocation, switchId: undefined },
            ];
            const splits: SwitchIdAndLocation[] = splittingState.splits.map((split) => ({
                location: split.location,
                switchId: split.switch.switchId,
            }));
            const switchesAndLocations = firstAndLast.concat(splits);

            return switchesAndLocations.map(({ location, switchId }) => {
                const isDeleted =
                    switches.find((sw) => sw.id === switchId)?.stateCategory === 'NOT_EXISTING';
                const feature = new Feature({
                    geometry: new OlPoint(pointToCoords(location)),
                });

                const isSwitchHighlighted =
                    splittingState.highlightedSwitch != undefined &&
                    splittingState.highlightedSwitch == switchId;

                const getSelectedStyle = () => {
                    if (isDeleted) {
                        return deletedSplitPointStyle;
                    } else if (isSwitchHighlighted) {
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
