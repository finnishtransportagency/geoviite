import * as React from 'react';
import { MapToolActivateOptions, MapToolWithButton } from 'map/tools/tool-model';
import OlMap from 'ol/Map';
import { expectCoordinate } from 'utils/type-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { MapToolButton } from 'map/tools/map-tool-button';
import { debounce } from 'ts-debounce';
import { MapLayer } from 'map/layers/utils/layer-model';

export const routingTool: MapToolWithButton = {
    id: 'route',
    activate: (map: OlMap, _layers: MapLayer[], options: MapToolActivateOptions) => {
        const debouncedMoveHandlerPointLocation = debounce(
            ({ coordinate }) => {
                options.onHoverLocation({
                    x: coordinate[0],
                    y: coordinate[1],
                });
            },
            10,
            {
                maxWait: 50,
            },
        );
        const clickEvent = map.on('click', ({ coordinate }) => {
            const [x, y] = expectCoordinate(coordinate);
            options.onClickLocation({
                x,
                y,
            });
        });
        const pointerMoveEvent = map.on('pointermove', debouncedMoveHandlerPointLocation);

        // Return function to clean up this tool
        return () => {
            map.un('click', clickEvent.listener);
            map.un('pointermove', pointerMoveEvent.listener);
        };
    },

    component: ({ isActive, setActiveTool }) => {
        return (
            <MapToolButton
                isActive={isActive}
                setActive={() => setActiveTool(routingTool)}
                icon={Icons.PositionPin}
            />
        );
    },
};
