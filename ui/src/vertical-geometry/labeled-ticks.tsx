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
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';
import { first } from 'utils/array-utils';
import { ifDefined } from 'utils/type-utils';

export interface LabeledTicksProps {
    trackKmHeights: TrackKmHeights[];
    coordinates: Coordinates;
    planLinkingSummary: PlanLinkingSummaryItem[] | undefined;
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

const LinkingDivider: React.FC<{
    coordinates: Coordinates;
    dividerPositionX: number;
}> = ({ coordinates, dividerPositionX }) => {
    return (
        <line
            x1={dividerPositionX}
            x2={dividerPositionX}
            y1={0}
            y2={coordinates.fullDiagramHeightPx}
            stroke="black"
            fill="none"
            shapeRendering="crispEdges"
        />
    );
};

const PlanLinkingDividers: React.FC<{
    planLinkingSummary: PlanLinkingSummaryItem[] | undefined;
    coordinates: Coordinates;
}> = ({ planLinkingSummary, coordinates }) => {
    return (
        <>
            {planLinkingSummary
                ?.filter(
                    (summary) =>
                        summary.startM <= coordinates.endM && summary.endM >= coordinates.startM,
                )
                .map((summary, i) => {
                    const planStartTickPosition = mToX(coordinates, summary.startM);

                    return (
                        <React.Fragment key={i}>
                            <LinkingDivider
                                coordinates={coordinates}
                                dividerPositionX={planStartTickPosition}
                            />

                            {/* The last plan linking summary won't have the divider that starts the next one. */}
                            {i === planLinkingSummary.length - 1 && (
                                <LinkingDivider
                                    coordinates={coordinates}
                                    dividerPositionX={mToX(coordinates, summary.endM)}
                                />
                            )}
                        </React.Fragment>
                    );
                })}
        </>
    );
};

export const LabeledTicks: React.FC<LabeledTicksProps> = ({
    trackKmHeights,
    coordinates,
    planLinkingSummary,
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

                            const firstTrackMeterHeight = ifDefined(
                                trackKmHeights[trackKmIndex + 1]?.trackMeterHeights,
                                first,
                            );
                            const hasSpaceBeforeNextKm =
                                trackKmIndex === trackKmHeights.length - 1 ||
                                meterIndex === 0 ||
                                (firstTrackMeterHeight &&
                                    firstTrackMeterHeight.m - m * coordinates.mMeterLengthPxOverM >
                                        minimumLabeledTickDistancePx);
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
        </>
    );
};
