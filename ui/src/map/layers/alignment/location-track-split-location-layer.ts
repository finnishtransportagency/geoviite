import { MapLayer } from 'map/layers/utils/layer-model';
import { Point as OlPoint } from 'ol/geom';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import Feature from 'ol/Feature';
import Style from 'ol/style/Style';
import { Circle, Fill, Stroke } from 'ol/style';
import mapStyles from 'map/map.module.scss';
import { clearFeatures, pointToCoords } from 'map/layers/utils/layer-utils';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { getSwitches } from 'track-layout/layout-switch-api';
import { LayoutSwitchId } from 'track-layout/track-layout-model';
import { Point } from 'model/geometry';

let newestLayerId = 0;

const splitPointStyle = new Style({
    image: new Circle({
        radius: 8,
        fill: new Fill({ color: mapStyles.splitPointCircleColor }),
        stroke: new Stroke({ color: mapStyles.alignmentBadgeWhite, width: 2 }),
    }),
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

export const createLocationTrackSplitLocationLayer = (
    existingOlLayer: VectorLayer<VectorSource<OlPoint>> | undefined,
    splittingState: SplittingState | undefined,
): MapLayer => {
    const layerId = ++newestLayerId;

    const vectorSource = existingOlLayer?.getSource() || new VectorSource();
    const layer = existingOlLayer || new VectorLayer({ source: vectorSource });
    if (splittingState) {
        getSwitches(
            splittingState.splits.map((sw) => sw.switchId),
            'DRAFT',
        ).then((switches) => {
            if (layerId !== newestLayerId) return;

            const firstAndLast: SwitchIdAndLocation[] = [
                { location: splittingState.firstSplit.location, switchId: undefined },
                { location: splittingState.endLocation, switchId: undefined },
            ];
            const splits: SwitchIdAndLocation[] = splittingState.splits.map((split) => ({
                location: split.location,
                switchId: split.switchId,
            }));
            const switchesAndLocations = firstAndLast.concat(splits);

            const features = switchesAndLocations.map(({ location, switchId }) => {
                const isDeleted =
                    switches.find((sw) => sw.id === switchId)?.stateCategory === 'NOT_EXISTING';
                const feature = new Feature({
                    geometry: new OlPoint(pointToCoords(location)),
                });
                feature.setStyle(isDeleted ? deletedSplitPointStyle : splitPointStyle);
                return feature;
            });

            clearFeatures(vectorSource);
            vectorSource.addFeatures(features);
        });
    } else {
        clearFeatures(vectorSource);
    }

    return {
        name: 'location-track-split-location-layer',
        layer: layer,
        requestInFlight: () => false,
    };
};
