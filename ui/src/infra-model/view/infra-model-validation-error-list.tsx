import React from 'react';
import styles from './form/infra-model-form.module.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import {
    CustomValidationError,
    ErrorType,
    ValidationResponse,
} from 'infra-model/infra-model-slice';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

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
            return Icons.StatusError;
        default:
            return exhaustiveMatchingGuard(errorType);
    }
};

const createErrorRow = (errorType: ErrorType, message: string, index: number) => {
    const Icon = getIcon(errorType);
    return (
        <li key={index}>
            <div className={styles['infra-model-upload__error']}>
                <Icon color={IconColor.INHERIT} size={IconSize.MEDIUM} />
                <span>{message}</span>
            </div>
        </li>
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
                {errors.length > 0 && (
                    <section>
                        <h2 className={styles['infra-model-upload__validation-error-title']}>
                            {t('im-form.validation-errors')}
                        </h2>
                        <ul className={styles['infra-model-upload__errors']}>
                            {errors.map((error: CustomValidationError, index: number) =>
                                createErrorRow(
                                    error.errorType,
                                    t(error.localizationKey, error),
                                    index,
                                ),
                            )}
                        </ul>
                    </section>
                )}
                {(major.length > 0 || minor.length > 0) && (
                    <section>
                        <h2 className={styles['infra-model-upload__validation-error-title']}>
                            {t('im-form.validation-warnings')}
                        </h2>
                        <ol className={styles['infra-model-upload__errors']}>
                            {major.map((error, index) =>
                                createErrorRow(
                                    error.errorType,
                                    t(error.localizationKey, error),
                                    index,
                                ),
                            )}

                            {minor.map((error, index) =>
                                createErrorRow(
                                    error.errorType,
                                    t(error.localizationKey, error),
                                    index,
                                ),
                            )}
                        </ol>
                    </section>
                )}
            </div>
        );
    };

    const noFileSelected = () => {
        return <div>No file selected</div>;
    };

    return (
        <React.Fragment>
            {validationResponse ? errorListDiv(validationResponse) : noFileSelected()}
        </React.Fragment>
    );
};

export default InfraModelValidationErrorList;
