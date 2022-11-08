import OlMap from 'ol/Map';
import { MapTool, MapToolActivateOptions } from './tool-model';
import { OlLayerAdapter } from 'map/layers/layer-model';
import { debounce } from 'ts-debounce';

export const pointLocationTool: MapTool = {
    icon: '',
    activate: (map: OlMap, _layerAdapters: OlLayerAdapter[], options: MapToolActivateOptions) => {
        const debouncedMoveHandlerPointLocation = debounce(
            (e) => {
                options.onHoverLocation({
                    x: e.coordinate[0],
                    y: e.coordinate[1],
                });
            },
            20,
            {
                maxWait: 100,
            },
        );
        const clickEvent = map.on('click', (e) => {
                options.onClickLocation(
                    {
                        x: e.coordinate[0],
                        y: e.coordinate[1],
                    },
                );
            },
        );
        const pointerMoveEvent = map.on('pointermove', debouncedMoveHandlerPointLocation);

        // Return function to clean up this tool
        return () => {
            map.un('click', clickEvent.listener);
            map.un('pointermove', pointerMoveEvent.listener);
        };
    },
};
