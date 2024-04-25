import styles from './track-layout.module.scss';
import * as React from 'react';
import { MapContext } from 'map/map-store';
import { SelectionPanelContainer } from 'selection-panel/selection-panel-container';
import { SwitchSuggestionCreatorContainer } from 'linking/switch-suggestion-creator-container';
import ToolPanelContainer from 'tool-panel/tool-panel-container';
import { createClassName } from 'vayla-design-lib/utils';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { MapViewContainer } from 'map/map-view-container';
import { VerticalGeometryDiagramContainer } from 'vertical-geometry/vertical-geometry-diagram-container';
import { ToolBarContainer } from 'tool-bar/tool-bar-container';
import { PrivilegeRequired } from 'user/privilege-required';
import { VIEW_GEOMETRY } from 'user/user-model';

export type TrackLayoutViewProps = {
    showVerticalGeometryDiagram: boolean;
};

export const TrackLayoutView: React.FC<TrackLayoutViewProps> = ({
    showVerticalGeometryDiagram,
}) => {
    const className = createClassName(
        styles['track-layout'],
        showVerticalGeometryDiagram && styles['track-layout--show-diagram'],
    );

    const [hoveredOverPlanSection, setHoveredOverPlanSection] =
        React.useState<HighlightedAlignment>();

    return (
        <div className={className} qa-id="track-layout-content">
            <ToolBarContainer />

            <div className={styles['track-layout__main-view']}>
                <div className={styles['track-layout__navi']}>
                    <SelectionPanelContainer />
                </div>

                {showVerticalGeometryDiagram && (
                    <PrivilegeRequired privilege={VIEW_GEOMETRY}>
                        <div className={styles['track-layout__diagram']}>
                            <VerticalGeometryDiagramContainer />
                        </div>
                    </PrivilegeRequired>
                )}

                <div className={styles['track-layout__map']}>
                    <MapContext.Provider value="track-layout">
                        <MapViewContainer hoveredOverPlanSection={hoveredOverPlanSection} />
                    </MapContext.Provider>
                </div>
                <div className={styles['track-layout__tool-panel']}>
                    <ToolPanelContainer setHoveredOverItem={setHoveredOverPlanSection} />
                </div>
            </div>

            <SwitchSuggestionCreatorContainer />
        </div>
    );
};
