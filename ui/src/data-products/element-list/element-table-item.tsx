import * as React from 'react';
import { TrackMeter } from 'common/common-model';
import { formatTrackMeter } from 'utils/geography-utils';
import { Precision, roundToPrecision } from 'utils/rounding';

export type ElementTableItemProps = {
    id: string;
    trackNumber: string | undefined;
    track: string;
    type: string;
    trackAddressStart: TrackMeter | undefined;
    trackAddressEnd: TrackMeter | undefined;
    locationStartE: number;
    locationStartN: number;
    locationEndE: number;
    locationEndN: number;
    length: number;
    curveRadiusStart: number | undefined;
    curveRadiusEnd: number | undefined;
    cantStart: number | undefined;
    cantEnd: number | undefined;
    angleStart: number | undefined;
    angleEnd: number | undefined;
    plan: string;
    source: string;
    coordinateSystem: string;
};

export const ElementTableItem: React.FC<ElementTableItemProps> = ({
    trackNumber,
    track,
    type,
    trackAddressStart,
    trackAddressEnd,
    coordinateSystem,
    locationStartE,
    locationStartN,
    locationEndE,
    locationEndN,
    length,
    curveRadiusStart,
    curveRadiusEnd,
    cantStart,
    cantEnd,
    angleStart,
    angleEnd,
    plan,
    source,
}) => {
    return (
        <React.Fragment>
            <tr>
                <td>{trackNumber}</td>
                <td>{track}</td>
                <td>{type}</td>
                <td>{trackAddressStart && formatTrackMeter(trackAddressStart)}</td>
                <td>{trackAddressEnd && formatTrackMeter(trackAddressEnd)}</td>
                <td>{coordinateSystem}</td>
                <td>{roundToPrecision(locationStartE, Precision.TM35FIN)}</td>
                <td>{roundToPrecision(locationStartN, Precision.TM35FIN)}</td>
                <td>{roundToPrecision(locationEndE, Precision.TM35FIN)}</td>
                <td>{roundToPrecision(locationEndN, Precision.TM35FIN)}</td>
                <td>{roundToPrecision(length, Precision.measurementMeterDistance)}</td>
                <td>
                    {curveRadiusStart && roundToPrecision(curveRadiusStart, Precision.curveMeters)}
                </td>
                <td>{curveRadiusEnd && roundToPrecision(curveRadiusEnd, Precision.curveMeters)}</td>
                <td>{cantStart && roundToPrecision(cantStart, Precision.cantMillimeters)}</td>
                <td>{cantEnd && roundToPrecision(cantEnd, Precision.cantMillimeters)}</td>
                <td>{angleStart && roundToPrecision(angleStart, Precision.angleFractions)}</td>
                <td>{angleEnd && roundToPrecision(angleEnd, Precision.angleFractions)}</td>
                <td>{plan}</td>
                <td>{source}</td>
            </tr>
        </React.Fragment>
    );
};
