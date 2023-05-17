import OlMap from 'ol/Map';
import { MapTool, MapToolActivateOptions } from './tool-model';
import { debounce } from 'ts-debounce';
import { MapLayer } from 'map/layers/utils/layer-model';

export const pointLocationTool: MapTool = {
    activate: (map: OlMap, _layers: MapLayer[], options: MapToolActivateOptions) => {
        const debouncedMoveHandlerPointLocation = debounce(
            (e) => {
                options.onHoverLocation({
                    x: e.coordinate[0],
                    y: e.coordinate[1],
                });
            },
            10,
            {
                maxWait: 50,
            },
        );
        const clickEvent = map.on('click', ({ coordinate }) => {
            options.onClickLocation({
                x: coordinate[0],
                y: coordinate[1],
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
