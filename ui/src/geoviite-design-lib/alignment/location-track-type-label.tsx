import React from 'react';
import { LocationTrackType } from 'track-layout/track-layout-model';
import { useTranslation } from 'react-i18next';

type LocationTrackTypeLabelProps = {
    type: LocationTrackType | null;
};

function getTranslationKey(locationTrackType: LocationTrackType | null) {
    switch (locationTrackType) {
        case 'MAIN':
        case 'SIDE':
        case 'TRAP':
        case 'CHORD':
            return locationTrackType;
        default:
            return 'UNKNOWN';
    }
}

const LocationTrackTypeLabel: React.FC<LocationTrackTypeLabelProps> = ({
    type,
}: LocationTrackTypeLabelProps) => {
    const { t } = useTranslation();

    return (
        <React.Fragment>{t(`enum.location-track-type.${getTranslationKey(type)}`)}</React.Fragment>
    );
};

export default LocationTrackTypeLabel;
