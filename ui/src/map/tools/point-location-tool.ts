import OlMap from 'ol/Map';
import { MapTool, MapToolActivateOptions } from './tool-model';
import { debounce } from 'ts-debounce';
import { MapLayer } from 'map/layers/utils/layer-model';
import { expectCoordinate } from 'utils/type-utils';

export const pointLocationTool: MapTool = {
    id: 'point-location',
    activate: (map: OlMap, _: MapLayer[], options: MapToolActivateOptions) => {
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
};
