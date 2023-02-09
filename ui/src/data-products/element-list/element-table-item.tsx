import * as React from 'react';
import { formatTrackMeter } from 'utils/geography-utils';
import { Precision, roundToPrecision } from 'utils/rounding';
import { TrackMeter } from 'common/common-model';

export type ElementTableItemProps = {
    id: string;
    trackNumber: string | undefined;
    geometryAlignmentName: string;
    type: string;
    locationTrackName: string;
    trackAddressStart: TrackMeter | null;
    trackAddressEnd: TrackMeter | null;
    locationStartE: number;
    locationStartN: number;
    locationEndE: number;
    locationEndN: number;
    length: number;
    curveRadiusStart: number | undefined;
    curveRadiusEnd: number | undefined;
    cantStart: number | null;
    cantEnd: number | null;
    angleStart: number;
    angleEnd: number;
    plan: string;
    source: string;
    coordinateSystem: string;

    showLocationTrackName: boolean;
};

export const ElementTableItem: React.FC<ElementTableItemProps> = ({
    trackNumber,
    geometryAlignmentName,
    locationTrackName,
    type,
    trackAddressStart,
    trackAddressEnd,
    coordinateSystem,
    locationStartE,
    locationStartN,
    locationEndE,
    locationEndN,
    curveRadiusStart,
    curveRadiusEnd,
    cantStart,
    cantEnd,
    angleStart,
    angleEnd,
    plan,
    source,
    showLocationTrackName,
}) => {
    return (
        <React.Fragment>
            <tr>
                <td>{trackNumber}</td>
                {showLocationTrackName && <td>{locationTrackName}</td>}
                <td>{geometryAlignmentName}</td>
                <td>{type}</td>
                <td>{trackAddressStart && formatTrackMeter(trackAddressStart)}</td>
                <td>{trackAddressEnd && formatTrackMeter(trackAddressEnd)}</td>
                <td>{coordinateSystem}</td>
                <td>{roundToPrecision(locationStartE, Precision.TM35FIN)}</td>
                <td>{roundToPrecision(locationStartN, Precision.TM35FIN)}</td>
                <td>{roundToPrecision(locationEndE, Precision.TM35FIN)}</td>
                <td>{roundToPrecision(locationEndN, Precision.TM35FIN)}</td>
                <td>{roundToPrecision(length, Precision.measurementMeterDistance)}</td>
                <td>{curveRadiusStart}</td>
                <td>{curveRadiusEnd}</td>
                <td>{cantStart && roundToPrecision(cantStart, Precision.cantMillimeters)}</td>
                <td>{cantEnd && roundToPrecision(cantEnd, Precision.cantMillimeters)}</td>
                <td>{angleStart}</td>
                <td>{angleEnd}</td>
                <td>{plan}</td>
                <td>{source}</td>
            </tr>
        </React.Fragment>
    );
};
