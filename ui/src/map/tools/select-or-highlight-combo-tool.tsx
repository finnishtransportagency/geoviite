import * as React from 'react';
import { SelectableMapTool } from 'map/tools/tool-model';
import { selectTool } from 'map/tools/select-tool';
import { highlightTool } from 'map/tools/highlight-tool';
import { createClassName } from 'vayla-design-lib/utils';
import styles from 'map/map.module.scss';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';

export const selectOrHighlightComboTool: SelectableMapTool = {
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
            <li
                onClick={() => setActiveTool(selectOrHighlightComboTool)}
                className={createClassName(
                    styles['map__map-tool'],
                    isActive && styles['map__map-tool--active'],
                )}>
                <Icons.Select color={IconColor.INHERIT} />
            </li>
        );
    },
};
