import React from 'react';
import { Draw } from 'ol/interaction';
import { Polygon as OlPolygon } from 'ol/geom';
import { Feature } from 'ol';
import { MapToolHandle, MapToolWithButton } from './tool-model';
import { LinkingState, LinkingType } from 'linking/linking-model';
import { coordsToPolygon } from 'model/geometry';
import { operationalPointPolygonStylesFunc } from 'map/layers/operational-point/operational-points-layer-utils';
import { getFeatureCoords } from 'map/layers/utils/layer-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { MapToolButton } from 'map/tools/map-tool-button';

function shouldDrawBeActive(linkingState: LinkingState | undefined): boolean {
    return linkingState?.type === LinkingType.PlacingOperationalPointArea && !linkingState.area;
}

const id = 'operational-point-area';
export const operationalPointAreaTool: MapToolWithButton = {
    id,
    customCursor: (linkingState: LinkingState | undefined) =>
        shouldDrawBeActive(linkingState) ? 'crosshair' : undefined,
    activate: (map, _, options): MapToolHandle => {
        const onSetOperationalPointPolygon = options.onSetOperationalPointPolygon;

        const draw = new Draw({
            type: 'Polygon',
            style: operationalPointPolygonStylesFunc('SELECTED', 'ADDING'),
        });

        draw.on('drawend', function (event) {
            const feature = event.feature as Feature<OlPolygon>;
            if (!feature) {
                return;
            }

            const coords = getFeatureCoords(feature);
            onSetOperationalPointPolygon(coordsToPolygon(coords));
        });

        draw.setActive(shouldDrawBeActive(options.linkingState));

        map.addInteraction(draw);

        return {
            deactivate: () => {
                map.removeInteraction(draw);
            },
            onLinkingStateChanged: (linkingState) => {
                draw.setActive(shouldDrawBeActive(linkingState));
            },
        };
    },
    component: ({ isActive, setActiveTool, disabled, hidden }) => {
        return (
            <MapToolButton
                id={id}
                isActive={isActive}
                setActive={setActiveTool}
                icon={Icons.Add}
                disabled={disabled}
                hidden={hidden}
            />
        );
    },
};
