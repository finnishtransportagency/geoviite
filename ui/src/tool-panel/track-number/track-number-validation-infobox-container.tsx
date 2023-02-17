import * as React from 'react';
import { getOfficialValidation } from 'publication/publication-api';
import { PublishRequestIds } from 'publication/publication-model';
import { useLoaderWithStatus } from 'utils/react-utils';
import { AssetValidationInfobox } from 'tool-panel/asset-validation-infobox';
import { LayoutTrackNumberId, ReferenceLineId } from 'track-layout/track-layout-model';

type TrackNumberValidationInfoboxProps = {
    trackNumberId: LayoutTrackNumberId;
    referenceLineId: ReferenceLineId | undefined;
};

const validationRequest = (
    trackNumberId: LayoutTrackNumberId,
    referenceLineId: ReferenceLineId | undefined,
): PublishRequestIds => ({
    trackNumbers: [trackNumberId],
    referenceLines: referenceLineId ? [referenceLineId] : [],
    locationTracks: [],
    switches: [],
    kmPosts: [],
});

export const TrackNumberValidationInfoboxContainer: React.FC<TrackNumberValidationInfoboxProps> = ({
    trackNumberId,
    referenceLineId,
}) => {
    const [validation, validationLoaderStatus] = useLoaderWithStatus(
        () =>
            getOfficialValidation(validationRequest(trackNumberId, referenceLineId)).then(
                (val) =>
                    val &&
                    (val.trackNumbers.find((tn) => tn.id === trackNumberId)?.errors ?? []).concat(
                        val.referenceLines.find((rl) => rl.id === referenceLineId)?.errors ?? [],
                    ),
            ),
        [trackNumberId, referenceLineId],
    );
    const errors = validation?.filter((err) => err.type === 'ERROR') || [];
    const warnings = validation?.filter((err) => err.type === 'WARNING') || [];

    return (
        <AssetValidationInfobox
            type={'TRACK_NUMBER'}
            errors={errors}
            warnings={warnings}
            validationLoaderStatus={validationLoaderStatus}
        />
    );
};
