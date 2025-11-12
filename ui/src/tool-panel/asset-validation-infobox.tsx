import * as React from 'react';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import Infobox from 'tool-panel/infobox/infobox';
import { LayoutValidationIssue } from 'publication/publication-model';
import { LoaderStatus } from 'utils/react-utils';
import { useTranslation } from 'react-i18next';
import styles from './asset-validation-infobox.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type AssetType =
    | 'TRACK_NUMBER'
    | 'REFERENCE_LINE'
    | 'LOCATION_TRACK'
    | 'SWITCH'
    | 'KM_POST'
    | 'OPERATIONAL_POINT';

type AssetValidationInfoboxProps = {
    type: AssetType;
    errors: LayoutValidationIssue[];
    warnings: LayoutValidationIssue[];
    validationLoaderStatus: LoaderStatus;
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
    children?: React.ReactNode;
};

const typePrefix = (type: AssetType) => {
    switch (type) {
        case 'TRACK_NUMBER':
            return 'tool-panel.validation.track-number-prefix';
        case 'REFERENCE_LINE':
            return 'tool-panel.validation.reference-line-prefix';
        case 'LOCATION_TRACK':
            return 'tool-panel.validation.location-track-prefix';
        case 'SWITCH':
            return 'tool-panel.validation.switch-prefix';
        case 'KM_POST':
            return 'tool-panel.validation.km-post-prefix';
        case 'OPERATIONAL_POINT':
            return 'tool-panel.validation.operational-point-prefix';
        default:
            return exhaustiveMatchingGuard(type);
    }
};

export const AssetValidationInfobox: React.FC<AssetValidationInfoboxProps> = ({
    errors,
    warnings,
    validationLoaderStatus,
    type,
    contentVisible,
    onContentVisibilityChange,
    children,
}) => {
    const { t } = useTranslation();

    const errorClassName = createClassName(
        'infobox__text',
        styles['asset-validation__error-status'],
    );
    const warningClassName = createClassName(
        'infobox__text',
        styles['asset-validation__warning-status'],
    );

    return (
        <Infobox
            contentVisible={contentVisible}
            onContentVisibilityChange={onContentVisibilityChange}
            title={`${t(typePrefix(type))} ${t('tool-panel.validation.integrity')}`}
            qa-id="location-track-log-infobox">
            <InfoboxContent>
                <ProgressIndicatorWrapper
                    indicator={ProgressIndicatorType.Area}
                    inProgress={validationLoaderStatus !== LoaderStatus.Ready}>
                    {errors.length === 0 && warnings.length === 0 ? (
                        <p className={'infobox__text'}>{t('tool-panel.validation.all-ok')}</p>
                    ) : (
                        <React.Fragment>
                            {errors.length > 0 && (
                                <React.Fragment>
                                    <p className={errorClassName}>
                                        <Icons.StatusError
                                            color={IconColor.INHERIT}
                                            size={IconSize.SMALL}
                                        />
                                        <span>{t('tool-panel.validation.errors')}</span>
                                    </p>
                                    {errors.map((err, index) => (
                                        <p className={'infobox__text'} key={index}>
                                            {t(err.localizationKey, err.params)}
                                        </p>
                                    ))}
                                </React.Fragment>
                            )}
                            {warnings.length > 0 && (
                                <React.Fragment>
                                    <p className={warningClassName}>
                                        {t('tool-panel.validation.warnings')}
                                    </p>
                                    {warnings.map((err, index) => (
                                        <p className={'infobox__text'} key={index}>
                                            {t(err.localizationKey, err.params)}
                                        </p>
                                    ))}
                                </React.Fragment>
                            )}
                        </React.Fragment>
                    )}
                </ProgressIndicatorWrapper>
                {children}
            </InfoboxContent>
        </Infobox>
    );
};
