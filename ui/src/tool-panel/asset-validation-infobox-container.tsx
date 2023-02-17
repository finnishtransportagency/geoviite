import * as React from 'react';
import { getOfficialValidation } from 'publication/publication-api';
import { AssetId, PublishRequestIds, ValidatedAssets } from 'publication/publication-model';
import { useLoaderWithStatus } from 'utils/react-utils';
import { AssetValidationInfobox } from 'tool-panel/asset-validation-infobox';

type AssetType = 'TRACK_NUMBER' | 'REFERENCE_LINE' | 'LOCATION_TRACK' | 'SWITCH' | 'KM_POST';

type AssetValidationInfoboxProps = {
    id: AssetId;
    type: AssetType;
};

const validationRequest = (id: AssetId, type: AssetType): PublishRequestIds => ({
    trackNumbers: type === 'TRACK_NUMBER' ? [id] : [],
    referenceLines: type === 'REFERENCE_LINE' ? [id] : [],
    locationTracks: type === 'LOCATION_TRACK' ? [id] : [],
    switches: type === 'SWITCH' ? [id] : [],
    kmPosts: type === 'KM_POST' ? [id] : [],
});

const unpackvalidation = (validation: ValidatedAssets, id: AssetId, type: AssetType) => {
    if (type === 'TRACK_NUMBER')
        return validation.trackNumbers.find((val) => val.id === id) ?? undefined;
    if (type === 'REFERENCE_LINE')
        return validation.referenceLines.find((val) => val.id === id) ?? undefined;
    if (type === 'LOCATION_TRACK')
        return validation.locationTracks.find((val) => val.id === id) ?? undefined;
    if (type === 'SWITCH') return validation.switches.find((val) => val.id === id) ?? undefined;
    if (type === 'KM_POST') return validation.kmPosts.find((val) => val.id === id) ?? undefined;
};

export const AssetValidationInfoboxContainer: React.FC<AssetValidationInfoboxProps> = ({
    id,
    type,
}) => {
    const [validation, validationLoaderStatus] = useLoaderWithStatus(
        () =>
            getOfficialValidation(validationRequest(id, type)).then(
                (val) => val && unpackvalidation(val, id, type),
            ),
        [id, type],
    );
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
