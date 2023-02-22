import * as React from 'react';
import { formatTrackMeter } from 'utils/geography-utils';
import { Precision, roundToPrecision } from 'utils/rounding';
import { CoordinateSystem, TrackMeter } from 'common/common-model';
import CoordinateSystemView from 'geoviite-design-lib/coordinate-system/coordinate-system-view';
import { useAppNavigate } from 'common/navigate';
import { Link } from 'vayla-design-lib/link/link';
import { GeometryPlanId } from 'geometry/geometry-model';

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
    coordinateSystem: CoordinateSystem | undefined;
    planId: GeometryPlanId;
    showLocationTrackName: boolean;
};

export const ElementTableItem: React.FC<ElementTableItemProps> = ({
    trackNumber,
    geometryAlignmentName,
    type,
    locationTrackName,
    trackAddressStart,
    trackAddressEnd,
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
    coordinateSystem,
    planId,
    showLocationTrackName,
}) => {
    const navigate = useAppNavigate();

    return (
        <React.Fragment>
            <tr>
                <td>{trackNumber}</td>
                {showLocationTrackName && <td>{locationTrackName}</td>}
                <td>{geometryAlignmentName}</td>
                <td>{type}</td>
                <td>{trackAddressStart && formatTrackMeter(trackAddressStart)}</td>
                <td>{trackAddressEnd && formatTrackMeter(trackAddressEnd)}</td>
                <td>
                    <CoordinateSystemView coordinateSystem={coordinateSystem} />
                </td>
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
                <td>
                    <Link onClick={() => navigate('inframodel-edit', planId)}>{plan}</Link>
                </td>
                <td>{source}</td>
            </tr>
        </React.Fragment>
    );
};
