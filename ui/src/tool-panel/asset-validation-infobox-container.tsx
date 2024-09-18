import * as React from 'react';
import { useLoaderWithStatus } from 'utils/react-utils';
import { AssetValidationInfobox } from 'tool-panel/asset-validation-infobox';
import { AssetId, LayoutContext, TimeStamp } from 'common/common-model';
import { getKmPostValidation } from 'track-layout/layout-km-post-api';
import { getSwitchValidation } from 'track-layout/layout-switch-api';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { ValidatedAsset, validationIssueIsError } from 'publication/publication-model';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
} from 'track-layout/track-layout-model';
import { getTrackNumberValidation } from 'track-layout/layout-track-number-api';

export type AssetIdAndType =
    | {
          type: 'TRACK_NUMBER';
          id: LayoutTrackNumberId;
      }
    | { type: 'SWITCH'; id: LayoutSwitchId }
    | { type: 'KM_POST'; id: LayoutKmPostId };

type AssetValidationInfoboxProps = {
    idAndType: AssetIdAndType;
    layoutContext: LayoutContext;
    changeTime: TimeStamp;
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
};

export const AssetValidationInfoboxContainer: React.FC<AssetValidationInfoboxProps> = ({
    idAndType,
    layoutContext,
    changeTime,
    contentVisible,
    onContentVisibilityChange,
}) => {
    const [validation, validationLoaderStatus] = useLoaderWithStatus<
        ValidatedAsset<AssetId> | undefined
    >(() => {
        const type = idAndType.type;
        switch (type) {
            case 'TRACK_NUMBER':
                return getTrackNumberValidation(layoutContext, idAndType.id);
            case 'KM_POST':
                return getKmPostValidation(layoutContext, idAndType.id);
            case 'SWITCH':
                return getSwitchValidation(layoutContext, idAndType.id);
            default:
                return exhaustiveMatchingGuard(type);
        }
    }, [
        idAndType.id,
        idAndType.type,
        layoutContext.publicationState,
        layoutContext.branch,
        changeTime,
    ]);
    const errors = validation?.errors.filter((err) => validationIssueIsError(err.type)) || [];
    const warnings = validation?.errors.filter((err) => !validationIssueIsError(err.type)) || [];

    return (
        <AssetValidationInfobox
            contentVisible={contentVisible}
            onContentVisibilityChange={onContentVisibilityChange}
            type={idAndType.type}
            errors={errors}
            warnings={warnings}
            validationLoaderStatus={validationLoaderStatus}
        />
    );
};
