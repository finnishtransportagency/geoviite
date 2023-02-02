import * as React from 'react';

export type ElementTableItemProps = {
    id: string;
    trackNumber: string;
    track: string;
    type: string;
    trackAddressStart: string;
    trackAddressEnd: string;
    locationStartE: string;
    locationStartN: string;
    locationEndE: string;
    locationEndN: string;
    length: string;
    curveRadiusStart: string;
    curveRadiusEnd: string;
    cantStart: string;
    cantEnd: string;
    angleStart: string;
    angleEnd: string;
    plan: string;
    coordinateSystem: string;
};

export const ElementTableItem: React.FC<ElementTableItemProps> = ({
    trackNumber,
    track,
    type,
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
    coordinateSystem,
}) => {
    return (
        <React.Fragment>
            <tr>
                <td>{trackNumber}</td>
                <td>{track}</td>
                <td>{type}</td>
                <td>{trackAddressStart}</td>
                <td>{trackAddressEnd}</td>
                <td>{locationStartE}</td>
                <td>{locationStartN}</td>
                <td>{locationEndE}</td>
                <td>{locationEndN}</td>
                <td>{length}</td>
                <td>{curveRadiusStart}</td>
                <td>{curveRadiusEnd}</td>
                <td>{cantStart}</td>
                <td>{cantEnd}</td>
                <td>{angleStart}</td>
                <td>{angleEnd}</td>
                <td>{plan}</td>
                <td>{coordinateSystem}</td>
            </tr>
        </React.Fragment>
    );
};
