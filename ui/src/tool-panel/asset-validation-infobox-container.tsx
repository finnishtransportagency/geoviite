import * as React from 'react';
import { useLoaderWithStatus } from 'utils/react-utils';
import { AssetValidationInfobox } from 'tool-panel/asset-validation-infobox';
import { AssetId, LayoutContext, TimeStamp } from 'common/common-model';
import { getKmPostValidation } from 'track-layout/layout-km-post-api';
import { getSwitchValidation } from 'track-layout/layout-switch-api';
import { getTrackNumberValidation } from 'track-layout/layout-track-number-api';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type AssetType = 'TRACK_NUMBER' | 'SWITCH' | 'KM_POST';

type AssetValidationInfoboxProps = {
    id: AssetId;
    type: AssetType;
    layoutContext: LayoutContext;
    changeTime: TimeStamp;
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
};

export const AssetValidationInfoboxContainer: React.FC<AssetValidationInfoboxProps> = ({
    id,
    type,
    layoutContext,
    changeTime,
    contentVisible,
    onContentVisibilityChange,
}) => {
    const [validation, validationLoaderStatus] = useLoaderWithStatus(() => {
        switch (type) {
            case 'TRACK_NUMBER':
                return getTrackNumberValidation(layoutContext, id);
            case 'KM_POST':
                return getKmPostValidation(layoutContext, id);
            case 'SWITCH':
                return getSwitchValidation(id, layoutContext);
            default:
                return exhaustiveMatchingGuard(type);
        }
    }, [id, type, layoutContext.publicationState, layoutContext.designId, changeTime]);
    const errors = validation?.errors.filter((err) => err.type === 'ERROR') || [];
    const warnings = validation?.errors.filter((err) => err.type === 'WARNING') || [];

    return (
        <AssetValidationInfobox
            contentVisible={contentVisible}
            onContentVisibilityChange={onContentVisibilityChange}
            type={type}
            errors={errors}
            warnings={warnings}
            validationLoaderStatus={validationLoaderStatus}
        />
    );
};
