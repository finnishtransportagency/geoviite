import * as React from 'react';
import {
    VerticalGeometryDiagram,
    VerticalGeometryDiagramAlignmentId,
} from 'vertical-geometry/vertical-geometry-diagram';
import { useAlignmentHeights } from 'vertical-geometry/km-heights-fetch';
import { ChangeTimes } from 'common/common-slice';
import { VerticalGeometryItem } from 'geometry/geometry-model';
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
import useResizeObserver from 'use-resize-observer';
import { OnSelectOptions } from 'selection/selection-model';
import { BoundingBox } from 'model/geometry';
import { processLayoutGeometries } from 'vertical-geometry/util';
import { useTranslation } from 'react-i18next';

type VerticalGeometryDiagramHolderProps = {
    alignmentId: VerticalGeometryDiagramAlignmentId;
    changeTimes: ChangeTimes;
    onCloseDiagram: () => void;
    onSelect: (options: OnSelectOptions) => void;
    showArea: (area: BoundingBox) => void;
};

// we don't really need the station values in the plan geometry for anything in this entire diagram
async function getStartAndEnd(alignmentId: VerticalGeometryDiagramAlignmentId) {
    return 'planId' in alignmentId
        ? getPlanAlignmentStartAndEnd(alignmentId.planId, alignmentId.alignmentId)
        : getLocationTrackStartAndEnd(alignmentId.locationTrackId, alignmentId.publishType);
}

async function getVerticalGeometry(
    changeTimes: ChangeTimes,
    alignmentId: VerticalGeometryDiagramAlignmentId,
): Promise<VerticalGeometryItem[] | null | undefined> {
    return 'planId' in alignmentId
        ? getGeometryPlanVerticalGeometry(changeTimes.geometryPlan, alignmentId.planId).then(
              (geometries) => geometries?.filter((g) => g.alignmentId == alignmentId.alignmentId),
          )
        : getLocationTrackVerticalGeometry(
              changeTimes.layoutLocationTrack,
              alignmentId.publishType,
              alignmentId.locationTrackId,
              undefined,
              undefined,
          );
}

export const VerticalGeometryDiagramHolder: React.FC<VerticalGeometryDiagramHolderProps> = ({
    alignmentId,
    changeTimes,
    onCloseDiagram,
    onSelect,
    showArea,
}) => {
    const [startM, setStartM] = React.useState<number>();
    const [endM, setEndM] = React.useState<number>();
    const [visibleStartM, setVisibleStartM] = React.useState<number>();
    const [visibleEndM, setVisibleEndM] = React.useState<number>();
    const [isLoading, setIsLoading] = React.useState(false);
    const [diagramHeight, setDiagramHeight] = React.useState<number>();
    const [diagramWidth, setDiagramWidth] = React.useState<number>();
    const [linkingSummary, setLinkingSummary] = React.useState<PlanLinkingSummaryItem[]>();
    const [processedGeometry, setProcessedGeometry] = React.useState<VerticalGeometryItem[]>();
    const { t } = useTranslation();

    const ref = React.useRef<HTMLDivElement>(null);

    const horizontalTickLengthMeters = minimumIntervalOrLongest(
        diagramWidth && visibleEndM && visibleStartM
            ? diagramWidth / (visibleEndM - visibleStartM)
            : 0,
        minimumApproximateHorizontalTickWidthPx,
    );

    const kmHeights = useAlignmentHeights(
        alignmentId,
        changeTimes,
        visibleStartM,
        visibleEndM,
        horizontalTickLengthMeters,
    );

    const showDiagram =
        !!kmHeights &&
        !!processedGeometry &&
        startM !== undefined &&
        endM !== undefined &&
        startM !== endM &&
        visibleStartM !== undefined &&
        visibleEndM !== undefined &&
        visibleStartM !== visibleEndM &&
        !!diagramWidth &&
        !!diagramHeight;

    React.useEffect(() => {
        let shouldUpdate = true;
        setIsLoading(true);

        const linkingSummaryPromise =
            'planId' in alignmentId
                ? undefined
                : getLocationTrackLinkingSummary(
                      changeTimes.layoutLocationTrack,
                      alignmentId.locationTrackId,
                      alignmentId.publishType,
                  );

        const geometryPromise = getVerticalGeometry(changeTimes, alignmentId);
        const startEndPromise = getStartAndEnd(alignmentId);

        Promise.all([linkingSummaryPromise, geometryPromise, startEndPromise]).then(
            async ([linkingSummary, geometry, startEnd]) => {
                if (geometry && shouldUpdate) {
                    const start = startEnd?.start?.point?.m ?? 0;
                    const end = startEnd?.end?.point?.m ?? 0;

                    setLinkingSummary(linkingSummary);

                    setProcessedGeometry(
                        (linkingSummary
                            ? processLayoutGeometries(geometry, linkingSummary)
                            : geometry
                        ).sort((a, b) => a.point.station - b.point.station),
                    );

                    setStartM(start);
                    setVisibleStartM(start);
                    setEndM(end);
                    setVisibleEndM(end);

                    setIsLoading(false);
                } else if (shouldUpdate) {
                    setStartM(undefined);
                    setVisibleStartM(undefined);
                    setEndM(undefined);
                    setVisibleEndM(undefined);
                    setProcessedGeometry(undefined);
                    setLinkingSummary(undefined);

                    setIsLoading(false);
                }
            },
        );

        return () => {
            shouldUpdate = false;
        };
    }, [alignmentId, changeTimes.layoutLocationTrack, changeTimes.geometryPlan]);

    useResizeObserver({
        ref,
        onResize: ({ width, height }) => {
            setDiagramWidth(width ?? 0);
            setDiagramHeight(height ?? 0);

            if (
                width === undefined ||
                visibleStartM === undefined ||
                visibleEndM === undefined ||
                diagramWidth === undefined
            ) {
                return;
            }

            const oldWidthRelativeEndM =
                visibleStartM + (visibleEndM - visibleStartM) * (width / diagramWidth);
            setEndM(endM ? Math.min(oldWidthRelativeEndM, endM) : oldWidthRelativeEndM);
        },
    });

    function onMove(startM: number, endM: number) {
        setVisibleStartM(startM);
        setVisibleEndM(endM);
    }

    return (
        <div ref={ref} className={styles['vertical-geometry-diagram-holder']}>
            <div
                className={styles['vertical-geometry-diagram-holder__close-icon']}
                onClick={onCloseDiagram}>
                <Icons.Close color={IconColor.INHERIT} size={IconSize.MEDIUM_SMALL} />
            </div>
            {isLoading && (
                <div className={styles['vertical-geometry-diagram-holder__backdrop']}></div>
            )}
            {!isLoading && !showDiagram && (
                <div className={styles['vertical-geometry-diagram-holder__no-diagram']}>
                    <span>{t('vertical-geometry-diagram.no-geometry')}</span>
                </div>
            )}
            {showDiagram && (
                <VerticalGeometryDiagram
                    kmHeights={kmHeights}
                    geometry={processedGeometry}
                    linkingSummary={linkingSummary}
                    startM={startM}
                    endM={endM}
                    visibleStartM={visibleStartM}
                    visibleEndM={visibleEndM}
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
