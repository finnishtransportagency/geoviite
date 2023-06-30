import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { VerticalGeometryDiagramAlignmentId } from 'vertical-geometry/vertical-geometry-diagram';
import { VerticalGeometryDiagramHolder } from './vertical-geometry-diagram-holder';
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';

export const VerticalGeometryDiagramContainer: React.FC = () => {
    const state = useTrackLayoutAppSelector((s) => s);
    const changeTimes = useCommonDataAppSelector((c) => c.changeTimes);
    const delegates = React.useMemo(() => createDelegates(trackLayoutActionCreators), []);
    const { t } = useTranslation();

    const [alignmentId, setAlignmentId] = React.useState<VerticalGeometryDiagramAlignmentId>();

    React.useEffect(() => {
        const selectedGeometryAlignment = state.selection.selectedItems.geometryAlignments[0];
        const selectedLocationTrack = state.selection.selectedItems.locationTracks[0];

        if (
            state.selectedToolPanelTab?.type === 'GEOMETRY_ALIGNMENT' &&
            selectedGeometryAlignment
        ) {
            setAlignmentId({
                planId: selectedGeometryAlignment.planId,
                alignmentId: selectedGeometryAlignment.geometryItem.id,
            });
        } else if (state.selectedToolPanelTab?.type === 'LOCATION_TRACK' && selectedLocationTrack) {
            setAlignmentId({
                locationTrackId: selectedLocationTrack,
                publishType: state.publishType,
            });
        } else {
            setAlignmentId(undefined);
        }
    }, [state.selectedToolPanelTab]);

    const closeDiagram = React.useMemo(() => {
        return () => delegates.onVerticalGeometryDiagramVisibilityChange(false);
    }, []);

    return (
        <>
            {alignmentId && (
                <VerticalGeometryDiagramHolder
                    alignmentId={alignmentId}
                    changeTimes={changeTimes}
                    onCloseDiagram={closeDiagram}
                    onSelect={delegates.onSelect}
                    showArea={delegates.showArea}
                />
            )}
            {!alignmentId && (
                <div className={styles['vertical-geometry-diagram-holder__not-alignment']}>
                    <span>{t('vertical-geometry-diagram.not-alignment')}</span>
                </div>
            )}
        </>
    );
};
