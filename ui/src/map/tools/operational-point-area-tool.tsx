import React from 'react';
import { Draw } from 'ol/interaction';
import { Polygon as OlPolygon } from 'ol/geom';
import { Feature } from 'ol';
import { DeactivateToolFn, MapToolActivateOptions, MapToolWithButton } from './tool-model';
import { LinkingType } from 'linking/linking-model';
import { coordsToPolygon } from 'model/geometry';
import { operationalPointPolygonStylesFunc } from 'map/layers/operational-point/operational-points-layer-utils';
import { getFeatureCoords } from 'map/layers/utils/layer-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { MapToolButton } from 'map/tools/map-tool-button';

export const operationalPointAreaTool: MapToolWithButton = {
    id: 'operational-point-area',
    customCursor: (options: MapToolActivateOptions) =>
        options.linkingState?.type === 'PlacingOperationalPointArea' && !options.linkingState.area
            ? 'crosshair'
            : undefined,
    activate: (map, _, options): DeactivateToolFn => {
        const linkingState = options.linkingState;
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

        draw.setActive(
            linkingState?.type === LinkingType.PlacingOperationalPointArea && !linkingState.area,
        );

        map.addInteraction(draw);

        return () => {
            map.removeInteraction(draw);
        };
    },
    component: ({ isActive, setActiveTool, disabled, hidden }) => {
        return (
            <MapToolButton
                isActive={isActive}
                setActive={() => setActiveTool(operationalPointAreaTool)}
                icon={Icons.Add}
                disabled={disabled}
                hidden={hidden}
            />
        );
    },
};
