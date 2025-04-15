import * as React from 'react';
import { VerticalGeometryDiagram } from 'vertical-geometry/vertical-geometry-diagram';
import { useAlignmentHeights } from 'vertical-geometry/km-heights-api';
import { ChangeTimes } from 'common/common-slice';
import { VerticalGeometryDiagramDisplayItem } from 'geometry/geometry-model';
import {
    getGeometryPlanVerticalGeometry,
    getLocationTrackLinkingSummary,
    getLocationTrackVerticalGeometry,
    getPlanAlignmentStartAndEnd,
    PlanLinkingSummaryItem,
} from 'geometry/geometry-api';
import {
    minimumApproximateHorizontalTickWidthPx,
    minimumIntervalOrLongest,
} from 'vertical-geometry/ticks-at-intervals';
import { getLocationTrackStartAndEnd } from 'track-layout/layout-location-track-api';
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { OnSelectOptions } from 'selection/selection-model';
import { BoundingBox } from 'model/geometry';
import { processLayoutGeometries, processPlanGeometries } from 'vertical-geometry/util';
import { useTranslation } from 'react-i18next';
import {
    planAlignmentKey,
    VerticalGeometryDiagramAlignmentId,
    VerticalGeometryDiagramGeometryAlignmentId,
    VerticalGeometryDiagramLayoutAlignmentId,
    VisibleExtentLookup,
} from 'vertical-geometry/store';
import { getMaxTimestamp } from 'utils/date-utils';
import { useUserHasPrivilege } from 'store/hooks';
import { VIEW_GEOMETRY_FILE } from 'user/user-model';
import { useResizeObserver } from 'utils/use-resize-observer';
import { TimeStamp } from 'common/common-model';

type VerticalGeometryDiagramHolderProps = {
    alignmentId: VerticalGeometryDiagramAlignmentId;
    changeTimes: ChangeTimes;
    onCloseDiagram: () => void;
    onSelect: (options: OnSelectOptions) => void;
    showArea: (area: BoundingBox) => void;

    setSavedVisibleExtentM: (
        visibleStartM: number | undefined,
        visibleEndM: number | undefined,
    ) => void;
    savedVisibleExtentLookup: VisibleExtentLookup;
};

type AlignmentAndExtents = {
    alignmentId: VerticalGeometryDiagramAlignmentId;
    startM: number;
    endM: number;
};

async function getStartAndEnd(
    changeTimes: ChangeTimes,
    alignmentId: VerticalGeometryDiagramAlignmentId,
) {
    return 'planId' in alignmentId
        ? getPlanAlignmentStartAndEnd(alignmentId.planId, alignmentId.alignmentId)
        : getLocationTrackStartAndEnd(
              alignmentId.locationTrackId,
              alignmentId.layoutContext,
              changeTimes.layoutLocationTrack,
          );
}

const getVerticalGeometryForGeometryPlan = (
    changeTime: TimeStamp,
    alignmentId: VerticalGeometryDiagramGeometryAlignmentId,
) =>
    getGeometryPlanVerticalGeometry(changeTime, alignmentId.planId).then((geometries) =>
        geometries?.filter((g) => g.alignmentId === alignmentId.alignmentId),
    );

const getVerticalGeometryForLayoutAlignment = (
    changeTime: TimeStamp,
    alignmentId: VerticalGeometryDiagramLayoutAlignmentId,
) =>
    getLocationTrackVerticalGeometry(
        changeTime,
        alignmentId.layoutContext,
        alignmentId.locationTrackId,
        undefined,
        undefined,
    );

export const VerticalGeometryDiagramHolder: React.FC<VerticalGeometryDiagramHolderProps> = ({
    alignmentId,
    changeTimes,
    onCloseDiagram,
    onSelect,
    showArea,
    setSavedVisibleExtentM,
    savedVisibleExtentLookup,
}) => {
    const [startM, setStartM] = React.useState<number>();
    const [endM, setEndM] = React.useState<number>();
    const [isLoadingGeometry, setIsLoadingGeometry] = React.useState(true);
    const [diagramHeight, setDiagramHeight] = React.useState<number>();
    const [diagramWidth, setDiagramWidth] = React.useState<number>();
    const [linkingSummary, setLinkingSummary] = React.useState<PlanLinkingSummaryItem[]>();
    const [processedGeometry, setProcessedGeometry] =
        React.useState<VerticalGeometryDiagramDisplayItem[]>();
    const [alignmentAndExtents, setAlignmentAndExtents] = React.useState<AlignmentAndExtents>();
    const { t } = useTranslation();

    const ref = React.useRef<HTMLDivElement>(null);

    const horizontalTickLengthMeters = minimumIntervalOrLongest(
        diagramWidth !== undefined &&
            alignmentAndExtents?.endM !== undefined &&
            alignmentAndExtents?.startM !== undefined
            ? diagramWidth / (alignmentAndExtents?.endM - alignmentAndExtents?.startM)
            : 0,
        minimumApproximateHorizontalTickWidthPx,
    );

    const geocodingChangeTime = getMaxTimestamp(
        changeTimes.layoutTrackNumber,
        changeTimes.layoutReferenceLine,
        changeTimes.layoutKmPost,
    );

    const kmHeights = useAlignmentHeights(
        alignmentAndExtents?.alignmentId,
        changeTimes,
        geocodingChangeTime,
        alignmentAndExtents?.startM,
        alignmentAndExtents?.endM,
        horizontalTickLengthMeters,
    );

    const showDiagram =
        kmHeights !== undefined &&
        kmHeights.length > 0 &&
        processedGeometry !== undefined &&
        startM !== undefined &&
        endM !== undefined &&
        startM !== endM &&
        !!diagramWidth &&
        !!diagramHeight;

    const canLoadGeometry = useUserHasPrivilege(VIEW_GEOMETRY_FILE);

    React.useEffect(() => {
        let shouldUpdate = true;
        if (!isLoadingGeometry) {
            setIsLoadingGeometry(true);
        }

        const linkingSummaryPromise =
            'planId' in alignmentId || !canLoadGeometry
                ? undefined
                : getLocationTrackLinkingSummary(
                      getMaxTimestamp(changeTimes.geometryPlan, changeTimes.layoutLocationTrack),
                      alignmentId.locationTrackId,
                      alignmentId.layoutContext,
                  );

        const geometryPromise = canLoadGeometry
            ? 'planId' in alignmentId
                ? getVerticalGeometryForGeometryPlan(changeTimes.geometryPlan, alignmentId)
                : getVerticalGeometryForLayoutAlignment(
                      getMaxTimestamp(
                          changeTimes.layoutLocationTrack,
                          changeTimes.geometryPlan,
                          geocodingChangeTime,
                      ),
                      alignmentId,
                  )
            : Promise.resolve([]);

        const startEndPromise = getStartAndEnd(changeTimes, alignmentId);

        Promise.all([linkingSummaryPromise, geometryPromise, startEndPromise]).then(
            async ([linkingSummary, geometry, startEnd]) => {
                if (geometry && shouldUpdate) {
                    const start = startEnd?.start?.point?.m ?? 0;
                    const end = startEnd?.end?.point?.m ?? 0;

                    setLinkingSummary(linkingSummary);

                    setProcessedGeometry(
                        (linkingSummary
                            ? processLayoutGeometries(geometry, linkingSummary)
                            : processPlanGeometries(geometry, startEnd?.staStart ?? 0)
                        ).sort((a, b) =>
                            !a.point || !b.point ? 0 : a.point.station - b.point.station,
                        ),
                    );

                    setStartM(start);
                    setEndM(end);
                    const savedVisibleExtent =
                        'planId' in alignmentId
                            ? savedVisibleExtentLookup.plan[
                                  planAlignmentKey(alignmentId.planId, alignmentId.alignmentId)
                              ]
                            : savedVisibleExtentLookup.layout[alignmentId.locationTrackId];

                    if (savedVisibleExtent) {
                        setAlignmentAndExtents({
                            alignmentId,
                            startM: savedVisibleExtent[0],
                            endM: savedVisibleExtent[1],
                        });
                    } else {
                        setAlignmentAndExtents({ alignmentId, startM: start, endM: end });
                    }

                    setIsLoadingGeometry(false);
                } else if (shouldUpdate) {
                    setStartM(undefined);
                    setEndM(undefined);
                    setSavedVisibleExtentM(undefined, undefined);
                    setProcessedGeometry(undefined);
                    setLinkingSummary(undefined);

                    setIsLoadingGeometry(false);
                }
            },
        );

        return () => {
            shouldUpdate = false;
        };
    }, [
        alignmentId,
        changeTimes.layoutLocationTrack,
        changeTimes.geometryPlan,
        geocodingChangeTime,
    ]);

    useResizeObserver({
        ref,
        onResize: ({ width, height }) => {
            setDiagramWidth(width ?? 0);
            setDiagramHeight(height ?? 0);
        },
    });

    const onMove = React.useCallback(
        function onMove(startM: number, endM: number) {
            setAlignmentAndExtents({ alignmentId, startM, endM });
            setSavedVisibleExtentM(startM, endM);
        },
        [setAlignmentAndExtents, setSavedVisibleExtentM, alignmentId],
    );

    return (
        <div ref={ref} className={styles['vertical-geometry-diagram-holder']}>
            <div
                className={styles['vertical-geometry-diagram-holder__close-icon']}
                onClick={onCloseDiagram}>
                <Icons.Close color={IconColor.INHERIT} size={IconSize.MEDIUM_SMALL} />
            </div>
            {isLoadingGeometry || kmHeights === undefined ? (
                <div className={styles['vertical-geometry-diagram-holder__backdrop']}></div>
            ) : !showDiagram ? (
                <div className={styles['vertical-geometry-diagram-holder__no-diagram']}>
                    <span>{t('vertical-geometry-diagram.no-geometry')}</span>
                </div>
            ) : (
                <VerticalGeometryDiagram
                    kmHeights={kmHeights}
                    geometry={processedGeometry}
                    linkingSummary={linkingSummary}
                    startM={startM}
                    endM={endM}
                    visibleStartM={alignmentAndExtents?.startM ?? startM}
                    visibleEndM={alignmentAndExtents?.endM ?? endM}
                    onMove={onMove}
                    showArea={showArea}
                    onSelect={onSelect}
                    horizontalTick={horizontalTickLengthMeters}
                    height={diagramHeight}
                    width={diagramWidth}
                />
            )}
        </div>
    );
};
