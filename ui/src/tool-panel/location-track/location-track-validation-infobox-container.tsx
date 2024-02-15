import * as React from 'react';
import { useLoaderWithStatus } from 'utils/react-utils';
import { AssetValidationInfobox } from 'tool-panel/asset-validation-infobox';
import { getLocationTrackValidation } from 'track-layout/layout-location-track-api';
import { PublishType, TimeStamp } from 'common/common-model';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { Button } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';

type LocationTrackValidationInfoboxProps = {
    id: LocationTrackId;
    publishType: PublishType;
    changeTime: TimeStamp;
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
    linkedSwitchesCount: number;
    showLinkedSwitchesRelinkingDialog: () => void;
};

export const LocationTrackValidationInfoboxContainer: React.FC<
    LocationTrackValidationInfoboxProps
> = ({
    id,
    publishType,
    changeTime,
    contentVisible,
    onContentVisibilityChange,
    showLinkedSwitchesRelinkingDialog,
}) => {
    const { t } = useTranslation();
    const [validation, validationLoaderStatus] = useLoaderWithStatus(
        () => getLocationTrackValidation(publishType, id),
        [id, publishType, changeTime],
    );

    const errors = validation?.errors.filter((err) => err.type === 'ERROR') || [];
    const warnings = validation?.errors.filter((err) => err.type === 'WARNING') || [];

    return (
        <AssetValidationInfobox
            contentVisible={contentVisible}
            onContentVisibilityChange={onContentVisibilityChange}
            type={'LOCATION_TRACK'}
            errors={errors}
            warnings={warnings}
            validationLoaderStatus={validationLoaderStatus}>
            <div>
                <Button onClick={showLinkedSwitchesRelinkingDialog}>
                    {t('tool-panel.location-track.open-switch-relinking-dialog')}
                </Button>
            </div>
        </AssetValidationInfobox>
    );
};
