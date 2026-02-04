import styles from './track-layout.module.scss';
import * as React from 'react';
import { MapContext } from 'map/map-store';
import { SelectionPanelContainer } from 'selection-panel/selection-panel-container';
import ToolPanelContainer from 'tool-panel/tool-panel-container';
import { createClassName } from 'vayla-design-lib/utils';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { MapViewContainer } from 'map/map-view-container';
import { VerticalGeometryDiagramContainer } from 'vertical-geometry/vertical-geometry-diagram-container';
import { ToolBarContainer } from 'tool-bar/tool-bar-container';
import { PrivilegeRequired } from 'user/privilege-required';
import { VIEW_GEOMETRY } from 'user/user-model';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { selectOrHighlightComboTool } from 'map/tools/select-or-highlight-combo-tool';
import { measurementTool } from 'map/tools/measurement-tool';
import { ConfirmMoveToMainOfficialDialogContainer } from 'map/plan-download/confirm-move-to-main-official-dialog';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { LinkingType } from 'linking/linking-model';

export type TrackLayoutViewProps = {
    showVerticalGeometryDiagram: boolean;
    enabled: boolean;
};

export const TrackLayoutView: React.FC<TrackLayoutViewProps> = ({
    showVerticalGeometryDiagram,
    enabled,
}) => {
    const className = createClassName(
        styles['track-layout'],
        showVerticalGeometryDiagram && styles['track-layout--show-diagram'],
    );

    const linkingState = useTrackLayoutAppSelector((s) => s.linkingState);
    const isPlacingOperationalPointArea =
        linkingState?.type === LinkingType.PlacingOperationalPointArea;

    const [hoveredOverPlanSection, setHoveredOverPlanSection] =
        React.useState<HighlightedAlignment>();
    const [switchToOfficialDialogOpen, setSwitchToOfficialDialogOpen] = React.useState(false);

    const mapTools = React.useMemo(
        () =>
            [selectOrHighlightComboTool, measurementTool].map((tool) => ({
                ...tool,
                disabled: isPlacingOperationalPointArea,
            })),
        [isPlacingOperationalPointArea],
    );

    return (
        <div className={className} qa-id="track-layout-content">
            <ToolBarContainer />

            <div className={'track-layout__progress-indicator-wrapper'}>
                <ProgressIndicatorWrapper
                    indicator={ProgressIndicatorType.Area}
                    inProgress={!enabled}
                    inline={false}>
                    <div className={styles['track-layout__main-view']}>
                        <div className={styles['track-layout__navi']}>
                            <SelectionPanelContainer
                                setSwitchToOfficialDialogOpen={setSwitchToOfficialDialogOpen}
                            />
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
                                <MapViewContainer
                                    hoveredOverPlanSection={hoveredOverPlanSection}
                                    mapTools={mapTools}
                                    customActiveMapTool={selectOrHighlightComboTool}
                                />
                            </MapContext.Provider>
                        </div>
                        <div className={styles['track-layout__tool-panel']}>
                            <ToolPanelContainer setHoveredOverItem={setHoveredOverPlanSection} />
                        </div>
                    </div>
                </ProgressIndicatorWrapper>
                {switchToOfficialDialogOpen && (
                    <ConfirmMoveToMainOfficialDialogContainer
                        onClose={() => setSwitchToOfficialDialogOpen(false)}
                    />
                )}
            </div>
        </div>
    );
};
