import * as React from 'react';
import { MapToolHandle, MapToolWithButton } from 'map/tools/tool-model';
import { selectTool } from 'map/tools/select-tool';
import { highlightTool } from 'map/tools/highlight-tool';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { MapToolButton } from 'map/tools/map-tool-button';

const id = 'select-or-highlight';
export const selectOrHighlightComboTool: MapToolWithButton = {
    id,
    activate: (map, layers, options): MapToolHandle => {
        const selectHandle = selectTool.activate(map, layers, options);
        const highlightHandle = highlightTool.activate(map, layers, options);

        return {
            deactivate: () => {
                selectHandle.deactivate();
                highlightHandle.deactivate();
            },
            onLayersChanged: (newLayers) => {
                selectHandle.onLayersChanged?.(newLayers);
                highlightHandle.onLayersChanged?.(newLayers);
            },
        };
    },

    component: ({ isActive, setActiveTool, disabled }) => {
        return (
            <MapToolButton
                id={id}
                isActive={isActive}
                setActive={setActiveTool}
                icon={Icons.Select}
                disabled={disabled}
            />
        );
    },
};
