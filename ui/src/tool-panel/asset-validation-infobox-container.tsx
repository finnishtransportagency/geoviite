import * as React from 'react';
import { useLoaderWithStatus } from 'utils/react-utils';
import { AssetValidationInfobox } from 'tool-panel/asset-validation-infobox';
import { AssetId, PublishType, TimeStamp } from 'common/common-model';
import { getLocationTrackValidation } from 'track-layout/layout-location-track-api';
import { getKmPostValidation } from 'track-layout/layout-km-post-api';
import { getSwitchValidation } from 'track-layout/layout-switch-api';
import { getTrackNumberValidation } from 'track-layout/layout-track-number-api';

type AssetType = 'TRACK_NUMBER' | 'REFERENCE_LINE' | 'LOCATION_TRACK' | 'SWITCH' | 'KM_POST';

type AssetValidationInfoboxProps = {
    id: AssetId;
    type: AssetType;
    publishType: PublishType;
    changeTime: TimeStamp;
};

export const AssetValidationInfoboxContainer: React.FC<AssetValidationInfoboxProps> = ({
    id,
    type,
    publishType,
    changeTime,
}) => {
    const [validation, validationLoaderStatus] = useLoaderWithStatus(() => {
        switch (type) {
            case 'LOCATION_TRACK':
                return getLocationTrackValidation(publishType, id);
            case 'TRACK_NUMBER':
                return getTrackNumberValidation(publishType, id);
            case 'KM_POST':
                return getKmPostValidation(publishType, id);
            case 'SWITCH':
                return getSwitchValidation(publishType, id);
            default:
                return Promise.resolve(undefined);
        }
    }, [id, type, publishType, changeTime]);
    const errors = validation?.errors.filter((err) => err.type === 'ERROR') || [];
    const warnings = validation?.errors.filter((err) => err.type === 'WARNING') || [];

    return (
        <AssetValidationInfobox
            type={type}
            errors={errors}
            warnings={warnings}
            validationLoaderStatus={validationLoaderStatus}
        />
    );
};
