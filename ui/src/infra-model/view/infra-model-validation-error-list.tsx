import React from 'react';
import styles from './form/infra-model-form.module.scss';
import { CustomValidationError, ErrorType, ValidationResponse } from 'api/inframodel-api';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxRow from 'tool-panel/infobox/infobox-row';
import { useTranslation } from 'react-i18next';

type InframodelValidationErrorListProps = {
    validationResponse: ValidationResponse | null;
};

const getIcon = (errorType: ErrorType) => {
    switch (errorType) {
        case 'REQUEST_ERROR':
        case 'PARSING_ERROR':
        case 'VALIDATION_ERROR':
        case 'TRANSFORMATION_ERROR':
            return Icons.StatusError;
        case 'OBSERVATION_MAJOR':
        case 'OBSERVATION_MINOR':
            return Icons.Info;
    }
};

const createErrorRow = (errorType: ErrorType, message: string, index: number) => {
    const Icon = getIcon(errorType);
    return (
        <InfoboxContent key={index}>
            <InfoboxRow>
                <div
                    className={styles['infra-model-upload-error__row']}>
                    <div className={styles['infra-model-upload-error__icon']}>
                        <Icon color={IconColor.INHERIT} size={IconSize.MEDIUM} />
                    </div>
                    <div className={styles['infra-model-upload-error__message']}>{message}</div>
                </div>
            </InfoboxRow>
        </InfoboxContent>
    );
};

const InfraModelValidationErrorList: React.FC<InframodelValidationErrorListProps> = ({
    validationResponse,
}: InframodelValidationErrorListProps) => {
    const { t } = useTranslation();
    const errorListDiv = (errorList: ValidationResponse) => {
        const errors = errorList.validationErrors.filter(
            (e) =>
                e.errorType === 'PARSING_ERROR' ||
                e.errorType === 'VALIDATION_ERROR' ||
                e.errorType === 'TRANSFORMATION_ERROR',
        );
        const major = errorList.validationErrors.filter((e) => e.errorType === 'OBSERVATION_MAJOR');
        const minor = errorList.validationErrors.filter((e) => e.errorType === 'OBSERVATION_MINOR');

        return (
            <div className={styles['infra-model-upload__validation-errors']}>
                <br />
                {errors.length > 0 && (
                    <React.Fragment>
                        <p className={styles['div__heading--medium']}>
                            {t('im-form.data-invalid')}
                        </p>
                        <p className={styles['div__heading--small']}>
                            {t('im-form.correct-data')}{' '}
                        </p>
                        {errors.map((error: CustomValidationError, index: number) =>
                            createErrorRow(error.errorType, t(error.localizationKey, error), index),
                        )}
                    </React.Fragment>
                )}
                {major.length > 0 && (
                    <React.Fragment>
                        <p className={styles['div__heading--medium']}>
                            {t('im-form.data-observation-major')}
                        </p>
                        {major.map((error: CustomValidationError, index: number) =>
                            createErrorRow(error.errorType, t(error.localizationKey, error), index),
                        )}
                    </React.Fragment>
                )}
                {minor.length > 0 && (
                    <React.Fragment>
                        <p className={styles['div__heading--medium']}>
                            {t('im-form.data-observation-minor')}
                        </p>
                        {minor.map((error: CustomValidationError, index: number) =>
                            createErrorRow(error.errorType, t(error.localizationKey, error), index),
                        )}
                    </React.Fragment>
                )}
            </div>
        );
    };

    const noFileSelected = () => {
        return <div>No file selected</div>;
    };

    return <div>{validationResponse ? errorListDiv(validationResponse) : noFileSelected()}</div>;
};

export default InfraModelValidationErrorList;
