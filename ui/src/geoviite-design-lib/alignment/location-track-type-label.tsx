import React from 'react';
import { LocationTrackType } from 'track-layout/track-layout-model';
import { useTranslation } from 'react-i18next';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type LocationTrackTypeLabelProps = {
    type: LocationTrackType | undefined;
};

function getTranslationKey(locationTrackType: LocationTrackType | undefined) {
    switch (locationTrackType) {
        case 'MAIN':
        case 'SIDE':
        case 'TRAP':
        case 'CHORD':
            return locationTrackType;
        case undefined:
            return 'UNKNOWN';
        default:
            return exhaustiveMatchingGuard(locationTrackType);
    }
}

const LocationTrackTypeLabel: React.FC<LocationTrackTypeLabelProps> = ({
    type,
}: LocationTrackTypeLabelProps) => {
    const { t } = useTranslation();

    return <span qa-id={type}>{t(`enum.LocationTrackType.${getTranslationKey(type)}`)}</span>;
};

export default LocationTrackTypeLabel;
