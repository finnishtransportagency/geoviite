import * as React from 'react';
import { TimeStamp } from 'common/common-model';
import styles from 'publication/publication-table-item.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { formatDateFull } from 'utils/date-utils';
import { useTranslation } from 'react-i18next';
import { Operation, PublicationId, PublishValidationError } from 'publication/publication-model';
import { createClassName } from 'vayla-design-lib/utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Menu, Item, useContextMenu } from 'react-contexify';
import { PreviewSelectType } from 'preview/preview-table';

export type PreviewTableItemProps = {
    id: PublicationId;
    type: PreviewSelectType;
    itemName: string;
    trackNumber?: string;
    errors: PublishValidationError[];
    changeTime: TimeStamp;
    operation: Operation | null;
    userName: string;
    pendingValidation: boolean;
    onPublishItemSelect?: () => void;
    onRevert: () => void;
    publish?: boolean;
};

export const PreviewTableItem: React.FC<PreviewTableItemProps> = ({
    id,
    type,
    itemName,
    trackNumber,
    errors,
    changeTime,
    operation,
    userName,
    pendingValidation,
    onPublishItemSelect,
    onRevert,
    publish = false,
}) => {
    const { t } = useTranslation();
    const [isErrorRowExpanded, setIsErrorRowExpanded] = React.useState(false);

    const errorsToStrings = (list: PublishValidationError[], type: 'ERROR' | 'WARNING') => {
        const filtered = list.filter((e) => e.type === type);
        return filtered.map((error) => t(error.localizationKey, error.params));
    };
    const menuId = () => `contextmenu_${id}_${type}`;
    const { show } = useContextMenu({
        id: menuId(),
    });

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
                <td>{formatDateFull(changeTime)}</td>
                <td>{userName}</td>
                {pendingValidation ? (
                    <td>
                        <Spinner />
                    </td>
                ) : (
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
                <td className={'preview-table-item preview-table-item__actions--cell'}>
                    <div>
                        <div>
                            <Button
                                variant={ButtonVariant.GHOST}
                                onClick={() => {
                                    onPublishItemSelect && onPublishItemSelect();
                                }}
                                icon={publish ? Icons.Ascending : Icons.Descending}
                            />
                        </div>
                        <div>
                            <Button
                                variant={ButtonVariant.GHOST}
                                icon={Icons.More}
                                onClick={(event: React.MouseEvent) => {
                                    show(event, {});
                                }}
                            />
                            <div>
                                <Menu animation={false} id={menuId()}>
                                    <Item id="1" onClick={() => onRevert()}>
                                        {t('publish.revert-change')}
                                    </Item>
                                </Menu>
                            </div>
                        </div>
                    </div>
                </td>
            </tr>
            {isErrorRowExpanded && hasErrors && (
                <tr className={'preview-table-item preview-table-item--error'}>
                    <td colSpan={7}>
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
