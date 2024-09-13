import * as React from 'react';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import styles from 'preview/preview-view.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { PublicationValidationState } from 'publication/publication-model';
import { createClassName } from 'vayla-design-lib/utils';
import { useTranslation } from 'react-i18next';

export type ValidationStateCellProps = {
    validationState: PublicationValidationState;
    hasErrors: boolean;
    toggleRowExpansion: () => void;
    errorTexts: string[];
    warningTexts: string[];
};

export const ValidationStateCell: React.FC<ValidationStateCellProps> = ({
    validationState,
    hasErrors,
    toggleRowExpansion,
    errorTexts,
    warningTexts,
}) => {
    const { t } = useTranslation();

    const statusCellClassName = createClassName(
        hasErrors && styles['preview-table-item__status-cell--expandable'],
    );

    switch (validationState) {
        case 'IN_PROGRESS':
            return (
                <td>
                    <Spinner />
                </td>
            );

        case 'API_CALL_ERROR':
            return (
                <td>
                    <span
                        title={t('preview-table.api-error-icon-hover-text')}
                        className={styles['preview-table-item__error-status']}>
                        <Icons.StatusError color={IconColor.INHERIT} size={IconSize.SMALL} />
                    </span>
                </td>
            );

        case 'API_CALL_OK':
            return (
                <td className={statusCellClassName} onClick={() => toggleRowExpansion()}>
                    {!hasErrors && (
                        <span className={styles['preview-table-item__ok-status']}>
                            <Icons.Tick color={IconColor.INHERIT} size={IconSize.SMALL} />
                        </span>
                    )}
                    <span>
                        {errorTexts.length > 0 && (
                            <span className={styles['preview-table-item__error-status']}>
                                {t('preview-table.errors-status-text', {
                                    errors: errorTexts.length,
                                })}
                            </span>
                        )}
                        {errorTexts.length > 0 && warningTexts.length > 0 && ' '}
                        {warningTexts.length > 0 && (
                            <span className={styles['preview-table-item__warning-status']}>
                                {t('preview-table.warnings-status-text', {
                                    warnings: warningTexts.length,
                                })}
                            </span>
                        )}
                    </span>
                </td>
            );

        default:
            return exhaustiveMatchingGuard(validationState);
    }
};
