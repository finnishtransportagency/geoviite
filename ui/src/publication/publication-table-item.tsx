import * as React from 'react';
import { TimeStamp } from 'common/common-model';
import styles from 'publication/publication-table-item.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { formatDateFull } from 'utils/date-utils';
import { useTranslation } from 'react-i18next';
import { Operation, PublishValidationError } from 'publication/publication-model';
import { createClassName } from 'vayla-design-lib/utils';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';

export type PreviewTableItemProps = {
    onChange: React.ChangeEventHandler<HTMLInputElement>;
    itemName: string;
    trackNumber?: string;
    errors: PublishValidationError[];
    changeTime: TimeStamp;
    showRatkoPushDate: boolean;
    showStatus: boolean;
    showActions: boolean;
    ratkoPushDate?: TimeStamp;
    operation: Operation | null;
    userName: string;
};

export const PublicationTableItem: React.FC<PreviewTableItemProps> = ({
    itemName,
    trackNumber,
    errors,
    changeTime,
    showRatkoPushDate,
    showStatus,
    showActions,
    ratkoPushDate,
    operation,
    userName,
}) => {
    const { t } = useTranslation();
    const [isErrorRowExpanded, setIsErrorRowExpanded] = React.useState(false);

    const errorsToStrings = (list: PublishValidationError[], type: 'ERROR' | 'WARNING') => {
        const filtered = list.filter((e) => e.type === type);
        return filtered.map((error) => t(error.localizationKey, error.params));
    };

    const errorTexts = errorsToStrings(errors, 'ERROR');
    const warningTexts = errorsToStrings(errors, 'WARNING');
    const hasErrors = errors.length > 0;

    const statusCellClassName = createClassName(
        styles['preview-table-item__status-cell'],
        hasErrors && styles['preview-table-item__status-cell--expandable'],
    );

    return (
        <React.Fragment>
            <tr className={'preview-table-item'}>
                <td>{itemName}</td>
                <td>{trackNumber ? trackNumber : ''}</td>
                <td>{operation ? t(`enum.publish-operation.${operation}`) : ''}</td>
                {showStatus && (
                    <td
                        className={statusCellClassName}
                        onClick={() => setIsErrorRowExpanded(!isErrorRowExpanded)}>
                        {!hasErrors && (
                            <span className={styles['preview-table-item__ok-status']}>
                                <Icons.Tick color={IconColor.INHERIT} size={IconSize.SMALL} />
                            </span>
                        )}
                        {errorTexts.length > 0 && (
                            <span className={styles['preview-table-item__error-status']}>
                                {t('publication-table.errors-status-text', [errorTexts.length])}
                            </span>
                        )}
                        {warningTexts.length > 0 && (
                            <span className={styles['preview-table-item__warning-status']}>
                                {t('publication-table.warnings-status-text', [warningTexts.length])}
                            </span>
                        )}
                    </td>
                )}
                <td>{userName}</td>
                <td>{formatDateFull(changeTime)}</td>
                {showRatkoPushDate && (
                    <td>{ratkoPushDate ? formatDateFull(ratkoPushDate) : t('no')}</td>
                )}
                {showActions && (
                    <td>
                        <div className={'preview-table-item__buttons'}>
                            <Button variant={ButtonVariant.GHOST} icon={Icons.Descending} />
                            <Button variant={ButtonVariant.GHOST} icon={Icons.More} />
                        </div>
                    </td>
                )}
            </tr>
            {isErrorRowExpanded && hasErrors && (
                <tr className={'preview-table-item preview-table-item--error'}>
                    <td colSpan={4}>
                        {errorTexts.length > 0 && (
                            <div className="preview-table-item__msg-group preview-table-item__msg-group--errors">
                                <div className="preview-table-item__group-title">
                                    {t('publication-table.errors-group-title')}
                                </div>
                                {errorTexts.map((errorText, index) => (
                                    <div key={index} className="preview-table-item__msg">
                                        {errorText}
                                    </div>
                                ))}
                            </div>
                        )}
                        {warningTexts.length > 0 && (
                            <div className="preview-table-item__msg-group preview-table-item__msg-group--warnings">
                                <div className="preview-table-item__group-title">
                                    {t('publication-table.warnings-group-title')}
                                </div>
                                {warningTexts?.map((warningText, index) => (
                                    <div key={index} className="preview-table-item__msg">
                                        {warningText}
                                    </div>
                                ))}
                            </div>
                        )}
                    </td>
                </tr>
            )}
        </React.Fragment>
    );
};
