import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { VerticalGeometryDiagramHolder } from './vertical-geometry-diagram-holder';
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';
import { planAlignmentKey, VerticalGeometryDiagramAlignmentId } from 'vertical-geometry/store';

export const VerticalGeometryDiagramContainer: React.FC = () => {
    const state = useTrackLayoutAppSelector((s) => s);
    const changeTimes = useCommonDataAppSelector((c) => c.changeTimes);
    const trackLayoutDelegates = React.useMemo(
        () => createDelegates(trackLayoutActionCreators),
        [],
    );

    const { t } = useTranslation();

    const [alignmentId, setAlignmentId] = React.useState<VerticalGeometryDiagramAlignmentId>();

    React.useEffect(() => {
        const selectedGeometryAlignment = state.selection.selectedItems.geometryAlignmentIds[0];
        const selectedLocationTrack = state.selection.selectedItems.locationTracks[0];

        if (selectedGeometryAlignment) {
            setAlignmentId({
                planId: selectedGeometryAlignment.planId,
                alignmentId: selectedGeometryAlignment.geometryId,
            });
        } else if (selectedLocationTrack) {
            setAlignmentId({
                locationTrackId: selectedLocationTrack,
                publishType: state.publishType,
            });
        } else {
            setAlignmentId(undefined);
        }
    }, [state.selectedToolPanelTab]);

    const closeDiagram = React.useCallback(
        () => trackLayoutDelegates.onVerticalGeometryDiagramVisibilityChange(false),
        [],
    );

    const setVisibleExtentM = function (startM: number | undefined, endM: number | undefined) {
        if (startM !== undefined && endM !== undefined && alignmentId !== undefined) {
            trackLayoutDelegates.onVerticalGeometryDiagramAlignmentVisibleExtentChange({
                alignmentId,
                extent: [startM, endM],
            });
        }
    };

    const diagramState = state.map.verticalGeometryDiagramState;
    const visibleExtent =
        alignmentId === undefined
            ? [undefined, undefined]
            : 'planId' in alignmentId
            ? diagramState.planAlignmentVisibleExtent[
                  planAlignmentKey(alignmentId.planId, alignmentId.alignmentId)
              ]
            : diagramState.layoutAlignmentVisibleExtent[alignmentId.locationTrackId];

    return (
        <>
            {alignmentId && (
                <VerticalGeometryDiagramHolder
                    alignmentId={alignmentId}
                    changeTimes={changeTimes}
                    onCloseDiagram={closeDiagram}
                    onSelect={trackLayoutDelegates.onSelect}
                    showArea={trackLayoutDelegates.showArea}
                    setVisibleExtentM={setVisibleExtentM}
                    visibleStartM={visibleExtent?.[0]}
                    visibleEndM={visibleExtent?.[1]}
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
