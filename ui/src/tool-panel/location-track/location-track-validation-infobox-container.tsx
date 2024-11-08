import * as React from 'react';
import { useLoaderWithStatus } from 'utils/react-utils';
import { AssetValidationInfobox } from 'tool-panel/asset-validation-infobox';
import { getLocationTrackValidation } from 'track-layout/layout-location-track-api';
import { LayoutContext } from 'common/common-model';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { useCommonDataAppSelector } from 'store/hooks';
import { EDIT_LAYOUT } from 'user/user-model';
import { PrivilegeRequired } from 'user/privilege-required';
import { validationIssueIsError } from 'publication/publication-model';

type LocationTrackValidationInfoboxProps = {
    id: LocationTrackId;
    layoutContext: LayoutContext;
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
    showLinkedSwitchesRelinkingDialog: () => void;
    editingDisabled: boolean;
};

export const LocationTrackValidationInfoboxContainer: React.FC<
    LocationTrackValidationInfoboxProps
> = ({
    id,
    layoutContext,
    contentVisible,
    onContentVisibilityChange,
    showLinkedSwitchesRelinkingDialog,
    editingDisabled,
}) => {
    const { t } = useTranslation();
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const [validation, validationLoaderStatus] = useLoaderWithStatus(
        () => getLocationTrackValidation(layoutContext, id),
        [id, layoutContext.publicationState, layoutContext.branch, changeTimes.layoutLocationTrack],
    );

    const errors = validation?.errors.filter((err) => validationIssueIsError(err.type)) || [];
    const warnings = validation?.errors.filter((err) => !validationIssueIsError(err.type)) || [];

    return (
        <AssetValidationInfobox
            contentVisible={contentVisible}
            onContentVisibilityChange={onContentVisibilityChange}
            type={'LOCATION_TRACK'}
            errors={errors}
            warnings={warnings}
            validationLoaderStatus={validationLoaderStatus}>
            {layoutContext.publicationState === 'DRAFT' && (
                <PrivilegeRequired privilege={EDIT_LAYOUT}>
                    <div>
                        <Button
                            size={ButtonSize.SMALL}
                            variant={ButtonVariant.SECONDARY}
                            disabled={editingDisabled}
                            onClick={showLinkedSwitchesRelinkingDialog}>
                            {t('tool-panel.location-track.open-switch-relinking-dialog')}
                        </Button>
                    </div>
                </PrivilegeRequired>
            )}
        </AssetValidationInfobox>
    );
};
