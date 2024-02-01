import * as React from 'react';
import { TimeStamp } from 'common/common-model';
import styles from './preview-view.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { formatDateFull } from 'utils/date-utils';
import { useTranslation } from 'react-i18next';
import {
    Operation,
    PublicationGroup,
    PublicationStage,
    PublishValidationError,
} from 'publication/publication-model';
import { createClassName } from 'vayla-design-lib/utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { ChangesBeingReverted } from 'preview/preview-view';
import { BoundingBox } from 'model/geometry';
import { Menu } from 'vayla-design-lib/menu/menu';

export type PreviewTableItemProps = {
    itemName: string;
    trackNumber?: string;
    errors: PublishValidationError[];
    changeTime: TimeStamp;
    operation?: Operation;
    publicationGroup?: PublicationGroup;
    userName: string;
    pendingValidation: boolean;
    onPublishItemSelect?: () => void;
    onRevert: () => void;
    changesBeingReverted: ChangesBeingReverted | undefined;
    publish?: boolean;
    boundingBox?: BoundingBox;
    stageAssetAmount: number;
    publicationGroupSize?: number;
    onShowOnMap: (bbox: BoundingBox) => void;
    setPublicationGroupStage: (publicationGroup: PublicationGroup, stage: PublicationStage) => void;
    setStageOfAllShownChanges: (stage: PublicationStage) => void;
};

export const PreviewTableItem: React.FC<PreviewTableItemProps> = ({
    itemName,
    trackNumber,
    errors,
    changeTime,
    operation,
    publicationGroup,
    userName,
    pendingValidation,
    onPublishItemSelect,
    onRevert,
    publish = false,
    changesBeingReverted,
    boundingBox,
    stageAssetAmount,
    publicationGroupSize,
    onShowOnMap,
    setPublicationGroupStage,
    setStageOfAllShownChanges,
}) => {
    const { t } = useTranslation();
    const [isErrorRowExpanded, setIsErrorRowExpanded] = React.useState(false);
    const [actionMenuVisible, setActionMenuVisible] = React.useState(false);

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

    const actionMenuRef = React.useRef(null);
    const moveTargetStage = publish ? PublicationStage.UNSTAGED : PublicationStage.STAGED;

    const movePublicationGroup = () => {
        publicationGroup && setPublicationGroupStage(publicationGroup, moveTargetStage);
        setActionMenuVisible(false);
    };

    const moveAllShownChanges = () => {
        setStageOfAllShownChanges(moveTargetStage);
        setActionMenuVisible(false);
    };

    const movePublicationGroupMenuOption = publicationGroup
        ? [
              {
                  onSelect: movePublicationGroup,
                  name: t('publish.move-publication-group', {
                      amount: publicationGroupSize,
                  }),
              },
          ]
        : [];

    const menuOptions = [
        ...movePublicationGroupMenuOption,
        {
            onSelect: moveAllShownChanges,
            name: t('publish.move-all-shown-changes', {
                amount: stageAssetAmount,
            }),
        },
        {
            disabled: !boundingBox,
            onSelect: () => {
                boundingBox && onShowOnMap(boundingBox);
                setActionMenuVisible(false);
            },
            name: t('publish.show-on-map'),
        },
        {
            name: t('publish.revert-change'),
            onSelect: () => {
                onRevert();
                setActionMenuVisible(false);
            },
        },
    ];

    return (
        <React.Fragment>
            <tr className={'preview-table-item'}>
                <td>
                    {publicationGroup?.id} {itemName}
                </td>
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
                                {t('preview-table.errors-status-text', {
                                    errors: errorTexts.length,
                                })}
                            </span>
                        )}
                        {warningTexts.length > 0 && (
                            <span className={styles['preview-table-item__warning-status']}>
                                {t('preview-table.warnings-status-text', {
                                    warnings: warningTexts.length,
                                })}
                            </span>
                        )}
                    </td>
                )}
                <td className={'preview-table-item preview-table-item__actions--cell'}>
                    <Button
                        qa-id={'stage-change-button'}
                        variant={ButtonVariant.GHOST}
                        onClick={() => {
                            onPublishItemSelect && onPublishItemSelect();
                        }}
                        icon={publish ? Icons.Ascending : Icons.Descending}
                    />
                    <React.Fragment>
                        {changesBeingReverted ? (
                            <div className={'preview-table-item__revert-spinner'}>
                                <Spinner />
                            </div>
                        ) : (
                            <Button
                                ref={actionMenuRef}
                                qa-id={'menu-button'}
                                variant={ButtonVariant.GHOST}
                                icon={Icons.More}
                                onClick={() => setActionMenuVisible(!actionMenuVisible)}
                            />
                        )}
                    </React.Fragment>
                </td>
            </tr>
            {isErrorRowExpanded && hasErrors && (
                <tr className={'preview-table-item preview-table-item--error'}>
                    <td colSpan={7}>
                        {errorTexts.length > 0 && (
                            <div className="preview-table-item__msg-group preview-table-item__msg-group--errors">
                                <div className="preview-table-item__group-title">
                                    {t('preview-table.errors-group-title')}
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
                                    {t('preview-table.warnings-group-title')}
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

            {actionMenuVisible && (
                <Menu
                    positionRef={actionMenuRef}
                    items={menuOptions}
                    onClickOutside={() => setActionMenuVisible(false)}
                />
            )}
        </React.Fragment>
    );
};
