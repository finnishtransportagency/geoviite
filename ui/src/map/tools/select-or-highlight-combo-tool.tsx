import * as React from 'react';
import { MapToolWithButton } from 'map/tools/tool-model';
import { selectTool } from 'map/tools/select-tool';
import { highlightTool } from 'map/tools/highlight-tool';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { MapToolButton } from 'map/tools/map-tool-button';

export const selectOrHighlightComboTool: MapToolWithButton = {
    id: 'select-or-highlight',
    activate: (map, layers, options) => {
        const deactivateSelect = selectTool.activate(map, layers, options);
        const deactivateHighlight = highlightTool.activate(map, layers, options);

        return () => {
            deactivateSelect();
            deactivateHighlight();
        };
    },

    component: ({ isActive, setActiveTool }) => {
        return (
            <MapToolButton
                isActive={isActive}
                setActive={() => setActiveTool(selectOrHighlightComboTool)}
                icon={Icons.Select}
            />
        );
    },
};
