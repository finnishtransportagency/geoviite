import React from 'react';
import { formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import { Translate } from 'vertical-geometry/translate';
import { lineGridStrokeColor } from 'vertical-geometry/height-lines';
import { Coordinates, mToX } from 'vertical-geometry/coordinates';
import { PlanLinkingSummaryItem, TrackKmHeights } from 'geometry/geometry-api';
import {
    minimumInterval,
    minimumIntervalOrLongest,
    minimumLabeledTickDistancePx,
} from 'vertical-geometry/ticks-at-intervals';
import { PlanLinkingItemHeader } from 'vertical-geometry/plan-linking-item-header';
import { OnSelectOptions } from 'selection/selection-model';
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';

export interface LabeledTicksProps {
    trackKmHeights: TrackKmHeights[];
    coordinates: Coordinates;
    planLinkingSummary: PlanLinkingSummaryItem[] | undefined;
    planLinkingOnSelect: (options: OnSelectOptions) => void;
}

const TickLabel: React.FC<{
    m: number;
    meter: number;
    kmNumber: string;
    coordinates: Coordinates;
}> = ({ m, kmNumber, meter, coordinates }) => {
    const x = mToX(coordinates, m);
    return (
        <>
            <Translate x={x - 50} y={coordinates.fullDiagramHeightPx - (meter === 0 ? 3 : 8)}>
                <text
                    className={styles['vertical-geometry-diagram__text-stroke-narrow']}
                    scale="0.7 0.7">
                    KM{' '}
                    {formatTrackMeterWithoutMeters({
                        kmNumber,
                        meters: meter,
                    })}
                </text>
                <line y={-40} stroke="black" />
            </Translate>
            <line
                x1={x}
                x2={x}
                y1={0}
                y2={coordinates.fullDiagramHeightPx - 20}
                stroke={lineGridStrokeColor}
                fill="none"
                shapeRendering="crispEdges"
            />
        </>
    );
};

const PlanLinkingDividers: React.FC<{
    planLinkingSummary: PlanLinkingSummaryItem[] | undefined;
    coordinates: Coordinates;
    startM: number;
    endM: number;
}> = ({ planLinkingSummary, coordinates, startM, endM }) => {
    if (!planLinkingSummary) {
        return <React.Fragment />;
    }

    return (
        <>
            {planLinkingSummary.map((summary, i) => {
                if (endM < coordinates.startM || startM > coordinates.endM) {
                    return <React.Fragment key={i} />;
                }

                const planStartTickPosition = mToX(coordinates, summary.startM);
                const planEndTickPosition = mToX(coordinates, endM);

                return (
                    <React.Fragment key={i}>
                        <line
                            x1={planStartTickPosition}
                            x2={planStartTickPosition}
                            y1={0}
                            y2={coordinates.fullDiagramHeightPx}
                            stroke="black"
                            fill="none"
                            shapeRendering="crispEdges"
                        />
                        <line
                            x1={planEndTickPosition}
                            x2={planEndTickPosition}
                            y1={0}
                            y2={coordinates.fullDiagramHeightPx}
                            stroke="black"
                            fill="none"
                            shapeRendering="crispEdges"
                        />
                    </React.Fragment>
                );
            })}
        </>
    );
};

const PlanLinkingHeaders: React.FC<{
    planLinkingSummary: PlanLinkingSummaryItem[] | undefined;
    planLinkingOnSelect: (options: OnSelectOptions) => void;
    coordinates: Coordinates;
    startM: number;
    endM: number;
}> = ({ planLinkingSummary, planLinkingOnSelect, coordinates, startM, endM }) => {
    if (!planLinkingSummary) {
        return <React.Fragment />;
    }

    return (
        <>
            {planLinkingSummary.map((summary, i) => {
                if (endM < coordinates.startM || startM > coordinates.endM) {
                    return <React.Fragment key={i} />;
                }

                return (
                    <PlanLinkingItemHeader
                        key={i}
                        coordinates={coordinates}
                        planLinkingSummaryItem={summary}
                        onSelect={planLinkingOnSelect}
                    />
                );
            })}
        </>
    );
};

export const LabeledTicks: React.FC<LabeledTicksProps> = ({
    trackKmHeights,
    coordinates,
    planLinkingSummary,
    planLinkingOnSelect,
}) => {
    // avoid rendering too far off the screen for performance
    const startM = coordinates.startM - 80 / coordinates.mMeterLengthPxOverM;
    const endM = coordinates.endM + 25 / coordinates.mMeterLengthPxOverM;

    // rough approximations based on m-meter = track meter, and track km = 1000 meters
    const trackKmLabelDisplayStep = minimumIntervalOrLongest(
        1000 * coordinates.mMeterLengthPxOverM,
        minimumLabeledTickDistancePx,
    );
    const trackMeterDisplayStep = minimumInterval(
        coordinates.mMeterLengthPxOverM,
        minimumLabeledTickDistancePx,
    );

    return (
        <>
            <PlanLinkingDividers
                planLinkingSummary={planLinkingSummary}
                coordinates={coordinates}
                startM={startM}
                endM={endM}
            />

            {trackKmHeights
                .filter(
                    ({ kmNumber }) =>
                        trackKmLabelDisplayStep === 1 ||
                        parseInt(kmNumber) % trackKmLabelDisplayStep === 0,
                )
                .flatMap(({ kmNumber, trackMeterHeights }, trackKmIndex) =>
                    trackMeterHeights
                        .filter(({ m, meter }, meterIndex) => {
                            const ordinaryTick = Number.isInteger(meter);
                            const inRenderedRange = m > startM && m < endM;
                            const hitsMeterStep =
                                meter === 0 ||
                                (trackMeterDisplayStep !== undefined &&
                                    meter % trackMeterDisplayStep === 0);
                            const hasSpaceBeforeNextKm =
                                trackKmIndex === trackKmHeights.length - 1 ||
                                meterIndex === 0 ||
                                (trackKmHeights[trackKmIndex + 1].trackMeterHeights[0].m - m) *
                                    coordinates.mMeterLengthPxOverM >
                                    minimumLabeledTickDistancePx;
                            return (
                                ordinaryTick &&
                                inRenderedRange &&
                                hitsMeterStep &&
                                hasSpaceBeforeNextKm
                            );
                        })
                        .map(({ m, meter }, meterIndex) => (
                            <TickLabel
                                key={`${trackKmIndex}_${meterIndex}`}
                                m={m}
                                meter={meter}
                                kmNumber={kmNumber}
                                coordinates={coordinates}
                            />
                        )),
                )}

            <PlanLinkingHeaders
                planLinkingSummary={planLinkingSummary}
                planLinkingOnSelect={planLinkingOnSelect}
                coordinates={coordinates}
                startM={startM}
                endM={endM}
            />
        </>
    );
};
