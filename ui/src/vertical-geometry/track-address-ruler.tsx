import React from 'react';
import { Coordinates, mToX } from 'vertical-geometry/coordinates';
import { TrackKmHeights } from 'geometry/geometry-api';
import { Translate } from 'vertical-geometry/translate';
import {
    minimumIntervalOrLongest,
    minimumRulerHeightLabelDistancePx,
} from 'vertical-geometry/ticks-at-intervals';
import styles from 'vertical-geometry/vertical-geometry-diagram.scss';
import { expectDefined } from 'utils/type-utils';
import { formatRounded } from 'utils/string-utils';

export interface TrackAddressRulerProps {
    kmHeights: TrackKmHeights[];
    heightPx: number;
    coordinates: Coordinates;
}

const KmPostMarker: React.FC<{ x: number; heightPx: number }> = ({ x, heightPx }) => (
    <Translate x={x} y={heightPx}>
        <circle r={5} stroke="black" fill="white" />
        <circle
            r={5}
            fill="black"
            clipPath="polygon(0 50%, 100% 50%, 100% 100%, 50% 100%, 50% 0, 0 0)"
        />
    </Translate>
);

export const TrackAddressRuler: React.FC<TrackAddressRulerProps> = ({
    kmHeights,
    heightPx,
    coordinates,
}) => {
    if (kmHeights.length === 0) {
        return <React.Fragment />;
    }
    const kmPostMarkers = kmHeights
        .filter(({ trackMeterHeights }) => trackMeterHeights[0]?.meter === 0)
        .map(({ trackMeterHeights }, kmIndex) => (
            <KmPostMarker
                key={kmIndex}
                x={mToX(coordinates, expectDefined(trackMeterHeights[0]).m)}
                heightPx={heightPx}
            />
        ));

    // avoid rendering too far off the screen for performance
    const startM = coordinates.startM - 5 / coordinates.mMeterLengthPxOverM;
    const endM = coordinates.endM + 5 / coordinates.mMeterLengthPxOverM;

    const kilometerTickLabelInterval = minimumIntervalOrLongest(
        coordinates.mMeterLengthPxOverM * coordinates.horizontalTickLengthMeters,
        minimumRulerHeightLabelDistancePx,
    );

    const initialRenderableTicks = kmHeights
        .filter(
            ({ kmNumber }) =>
                kilometerTickLabelInterval === 1 ||
                parseInt(kmNumber) % kilometerTickLabelInterval === 1,
        )
        .flatMap(({ kmNumber, trackMeterHeights }) =>
            trackMeterHeights
                .filter(({ m }) => m > startM && m < endM)
                .map((tm) => ({ kmNumber, ...tm, x: mToX(coordinates, tm.m) })),
        );

    const ticks = initialRenderableTicks.map(({ height, x }, i) => {
        return (
            <g transform={`translate(${x} ${heightPx})`} key={`${i}`}>
                <line y2={-5} stroke={'black'} />
                {height && (
                    <text
                        className={styles['vertical-geometry-diagram__text-stroke-wide']}
                        transform={`translate(3 -6) scale(0.7) rotate(-90)`}>
                        {formatRounded(height, 2)}
                    </text>
                )}
            </g>
        );
    });

    return (
        <>
            {ticks}
            {kmPostMarkers}
            <line
                x1={0}
                x2={coordinates.diagramWidthPx}
                y1={heightPx}
                y2={heightPx}
                stroke="black"
                fill="none"
                shapeRendering="crispEdges"
            />
        </>
    );
};
