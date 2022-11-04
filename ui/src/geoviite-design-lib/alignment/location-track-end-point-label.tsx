import React from 'react';
import { useTranslation } from 'react-i18next';
import { LayoutEndPoint } from 'common/common-model';

type LocationTrackEndPointLabelProps = {
    endPointType: LayoutEndPoint | undefined;
};

function getTranslationKey(endPoint: LayoutEndPoint | undefined) {
    switch (endPoint) {
        case LayoutEndPoint.SWITCH:
        case LayoutEndPoint.LOCATION_TRACK:
        case LayoutEndPoint.ENDPOINT:
            return endPoint;
        default:
            return 'UNKNOWN';
    }
}

const LocationTrackEndPointLabel: React.FC<LocationTrackEndPointLabelProps> = ({
    endPointType,
}: LocationTrackEndPointLabelProps) => {
    const { t } = useTranslation();

    const typeName = t(`enum.alignment-end-type.${getTranslationKey(endPointType)}`);

    return <React.Fragment>{typeName}</React.Fragment>;
};

export default LocationTrackEndPointLabel;
