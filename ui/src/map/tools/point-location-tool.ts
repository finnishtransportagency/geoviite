import OlMap from 'ol/Map';
import { MapTool, MapToolActivateOptions } from './tool-model';
import { debounce } from 'ts-debounce';
import { MapLayer } from 'map/layers/utils/layer-model';
import { expectCoordinate } from 'utils/type-utils';
import { brand } from 'common/brand';
import { MapBrowserEvent } from 'ol';

export const pointLocationTool: MapTool = {
    activate: (map: OlMap, _: MapLayer[], options: MapToolActivateOptions) => {
        const debouncedMoveHandlerPointLocation: (event: MapBrowserEvent<PointerEvent>) => void =
            debounce(
                ({ coordinate, originalEvent }) => {
                    if (coordinate[0] !== undefined && coordinate[1] !== undefined) {
                        options.onHoverLocation(
                            {
                                x: coordinate[0],
                                y: coordinate[1],
                            },
                            /* MouseEvent x and y are really clientX/clientY, aka in viewport/client coordinates */
                            brand({ x: originalEvent.x, y: originalEvent.y }),
                        );
                    }
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
