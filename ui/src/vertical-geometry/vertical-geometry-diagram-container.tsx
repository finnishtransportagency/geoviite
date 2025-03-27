import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { VerticalGeometryDiagramHolder } from './vertical-geometry-diagram-holder';
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';
import { VerticalGeometryDiagramAlignmentId } from 'vertical-geometry/store';
import { first } from 'utils/array-utils';
import { GeometryAlignmentId, GeometryPlanId } from 'geometry/geometry-model';
import { LocationTrackId } from 'track-layout/track-layout-model';

export const VerticalGeometryDiagramContainer: React.FC = () => {
    const state = useTrackLayoutAppSelector((s) => s);
    const changeTimes = useCommonDataAppSelector((c) => c.changeTimes);
    const trackLayoutDelegates = React.useMemo(
        () => createDelegates(trackLayoutActionCreators),
        [],
    );

    const { t } = useTranslation();

    const [selectedAlignment, setSelectedAlignment] =
        React.useState<VerticalGeometryDiagramAlignmentSelection>();

    React.useEffect(() => {
        const selectedGeometryAlignment = first(state.selection.selectedItems.geometryAlignmentIds);
        const selectedLocationTrack = first(state.selection.selectedItems.locationTracks);

        if (
            state.selectedToolPanelTab?.type === 'GEOMETRY_ALIGNMENT' &&
            selectedGeometryAlignment
        ) {
            setSelectedAlignment({
                planId: selectedGeometryAlignment.planId,
                alignmentId: selectedGeometryAlignment.geometryId,
            });
        } else if (state.selectedToolPanelTab?.type === 'LOCATION_TRACK' && selectedLocationTrack) {
            setSelectedAlignment({
                locationTrackId: selectedLocationTrack,
            });
        } else {
            setSelectedAlignment(undefined);
        }
    }, [state.selectedToolPanelTab]);

    const closeDiagram = React.useCallback(
        () => trackLayoutDelegates.onVerticalGeometryDiagramVisibilityChange(false),
        [],
    );

    const alignmentId: VerticalGeometryDiagramAlignmentId | undefined =
        selectedAlignment === undefined
            ? undefined
            : 'locationTrackId' in selectedAlignment
              ? { ...selectedAlignment, layoutContext: state.layoutContext }
              : selectedAlignment;

    const setVisibleExtentM = function (startM: number | undefined, endM: number | undefined) {
        if (startM !== undefined && endM !== undefined && alignmentId !== undefined) {
            trackLayoutDelegates.onVerticalGeometryDiagramAlignmentVisibleExtentChange({
                alignmentId,
                extent: [startM, endM],
            });
        }
    };

    return (
        <>
            {alignmentId && (
                <VerticalGeometryDiagramHolder
                    alignmentId={alignmentId}
                    changeTimes={changeTimes}
                    onCloseDiagram={closeDiagram}
                    onSelect={trackLayoutDelegates.onSelect}
                    showArea={trackLayoutDelegates.showArea}
                    setSavedVisibleExtentM={setVisibleExtentM}
                    savedVisibleExtentLookup={
                        state.map.verticalGeometryDiagramState.visibleExtentLookup
                    }
                />
            )}
            {!selectedAlignment && (
                <div className={styles['vertical-geometry-diagram-holder__not-alignment']}>
                    <span>{t('vertical-geometry-diagram.not-alignment')}</span>
                </div>
            )}
        </>
    );
};

type VerticalGeometryDiagramAlignmentSelection =
    | { planId: GeometryPlanId; alignmentId: GeometryAlignmentId }
    | { locationTrackId: LocationTrackId };
