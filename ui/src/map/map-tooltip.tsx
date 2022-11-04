import React from 'react';
import OlMap from 'ol/Map';
import { debounce } from 'ts-debounce';
import { OlLayerAdapter } from 'map/layers/layer-model';
import styles from 'map/map.module.scss';
import { getDefaultHitArea, searchShownItemsFromLayers } from 'map/tools/tool-utils';
import { OptionalShownItems } from 'map/map-model';

export type MapTooltipProps = {
    map: OlMap | null;
    layerAdapters: OlLayerAdapter[];
};

export const MapTooltip: React.FC<MapTooltipProps> = ({ map, layerAdapters }: MapTooltipProps) => {
    const [tooltipItems, setTooltipItems] = React.useState<OptionalShownItems>({});
    const [tooltipLocation, setTooltipLocation] = React.useState<{ x: number; y: number }>({
        x: 0,
        y: 0,
    });

    React.useEffect(() => {
        if (!map) {
            return;
        }

        const debouncedMoveHandler = debounce((e) => {
            setTooltipLocation({ x: e.pixel[0], y: e.pixel[1] });
            const hitArea = getDefaultHitArea(map, e.coordinate);
            const items = searchShownItemsFromLayers(hitArea, layerAdapters, {
                limit: 5, // For now pick max five of each type
            });
            setTooltipItems(items);
        }, 200);
        const pointerMoveEvent = map.on('pointermove', debouncedMoveHandler);

        return () => {
            map.un('pointermove', pointerMoveEvent.listener);
        };
    }, [map, layerAdapters]);

    return (
        <div
            className={styles['map__tooltip-anchor']}
            style={{ left: tooltipLocation.x, top: tooltipLocation.y }}>
            <div className={styles['map__tooltip']}>
                {tooltipItems.locationTracks?.map((locationTrack) => (
                    <div key={locationTrack.id} className={styles['map__tooltip-item']}>
                        {locationTrack.name}
                    </div>
                ))}
                {tooltipItems.switches?.map((s) => (
                    <div key={s.id} className={styles['map__tooltip-item']}>
                        {s.name}
                    </div>
                ))}
            </div>
        </div>
    );
};
