import * as React from 'react';
import styles from './preview-view.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { formatDateFull } from 'utils/date-utils';
import { useTranslation } from 'react-i18next';
import {
    PublicationStage,
    PublishRequestIds,
    PublishValidationError,
} from 'publication/publication-model';
import { createClassName } from 'vayla-design-lib/utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import {
    ChangesBeingReverted,
    PreviewOperations,
    PublicationAssetChangeAmounts,
    RevertRequestSource,
} from 'preview/preview-view';
import { Menu } from 'vayla-design-lib/menu/menu';
import { PreviewSelectType, PreviewTableEntry, PublicationId } from 'preview/preview-table';
import { BoundingBox } from 'model/geometry';

const createPublishRequestIdsFromTableEntry = (
    id: PublicationId,
    type: PreviewSelectType,
): PublishRequestIds => ({
    trackNumbers: type === PreviewSelectType.trackNumber ? [id] : [],
    referenceLines: type === PreviewSelectType.referenceLine ? [id] : [],
    locationTracks: type === PreviewSelectType.locationTrack ? [id] : [],
    switches: type === PreviewSelectType.switch ? [id] : [],
    kmPosts: type === PreviewSelectType.kmPost ? [id] : [],
});

const conditionalMenuOption = (
    condition: unknown | undefined,
    menuOption: MenuSelectOption,
): MenuSelectOption[] => (condition ? [menuOption] : []);

export type PreviewTableItemProps = {
    tableEntry: PreviewTableEntry;
    publish?: boolean;
    changesBeingReverted: ChangesBeingReverted | undefined;
    previewOperations: PreviewOperations;
    publicationAssetChangeAmounts: PublicationAssetChangeAmounts;
    onShowOnMap: (bbox: BoundingBox) => void;
};

export const PreviewTableItem: React.FC<PreviewTableItemProps> = ({
    tableEntry,
    publish = false,
    changesBeingReverted,
    previewOperations,
    publicationAssetChangeAmounts,
    onShowOnMap,
}) => {
    const { t } = useTranslation();
    const [isErrorRowExpanded, setIsErrorRowExpanded] = React.useState(false);
    const [actionMenuVisible, setActionMenuVisible] = React.useState(false);

    const errorsToStrings = (list: PublishValidationError[], type: 'ERROR' | 'WARNING') => {
        const filtered = list.filter((e) => e.type === type);
        return filtered.map((error) => t(error.localizationKey, error.params));
    };
    const errorTexts = errorsToStrings(tableEntry.errors, 'ERROR');
    const warningTexts = errorsToStrings(tableEntry.errors, 'WARNING');
    const hasErrors = tableEntry.errors.length > 0;

    const statusCellClassName = createClassName(
        styles['preview-table-item__status-cell'],
        hasErrors && styles['preview-table-item__status-cell--expandable'],
    );

    const actionMenuRef = React.useRef(null);

    const publicationGroupAssetAmount = tableEntry.publicationGroup
        ? publicationAssetChangeAmounts.groupAmounts[tableEntry.publicationGroup?.id]
        : undefined;

    const [displayedPublicationStage, moveTargetStage] = publish
        ? [PublicationStage.STAGED, PublicationStage.UNSTAGED]
        : [PublicationStage.UNSTAGED, PublicationStage.STAGED];

    const stagePublicationAssetAmount = publish
        ? publicationAssetChangeAmounts.staged
        : publicationAssetChangeAmounts.unstaged;

    const tableEntryAsPublishRequestIds = createPublishRequestIdsFromTableEntry(
        tableEntry.id,
        tableEntry.type,
    );

    const tableEntryAsRevertRequestSource: RevertRequestSource = {
        id: tableEntry.id,
        type: tableEntry.type,
        name: tableEntry.name,
    };

    const menuAction = (menuActionFunction: () => void) => (): void => {
        menuActionFunction();
        setActionMenuVisible(false);
    };

    const menuOptionMoveStageChanges: MenuSelectOption = {
        onSelect: menuAction(() =>
            previewOperations.setPublicationStage.forAllStageChanges(
                displayedPublicationStage,
                moveTargetStage,
            ),
        ),
        name: t('publish.move-stage-changes', {
            amount: stagePublicationAssetAmount,
        }),
    };

    const menuOptionMovePublicationGroupStage: MenuSelectOption = {
        onSelect: menuAction(() => {
            if (tableEntry.publicationGroup) {
                previewOperations.setPublicationStage.forPublicationGroup(
                    tableEntry.publicationGroup,
                    moveTargetStage,
                );
            }
        }),
        name: t('publish.move-publication-group', {
            amount: publicationGroupAssetAmount,
        }),
    };

    const menuOptionRevertSingleChange: MenuSelectOption = {
        name: t('publish.revert-change'),
        onSelect: menuAction(() =>
            previewOperations.revert.changesWithDependencies(
                tableEntryAsPublishRequestIds,
                tableEntryAsRevertRequestSource,
            ),
        ),
    };

    const menuOptionRevertStageChanges: MenuSelectOption = {
        onSelect: menuAction(() => {
            previewOperations.revert.stageChanges(
                displayedPublicationStage,
                tableEntryAsRevertRequestSource,
            );
        }),
        name: t('publish.revert-stage-changes', {
            amount: stagePublicationAssetAmount,
        }),
    };

    const menuOptionPublicationGroupRevert: MenuSelectOption = {
        onSelect: menuAction(() => {
            if (tableEntry.publicationGroup) {
                previewOperations.revert.publicationGroup(
                    tableEntry.publicationGroup,
                    tableEntryAsRevertRequestSource,
                );
            }
        }),
        name: t('publish.revert-publication-group', {
            amount: publicationGroupAssetAmount,
        }),
    };

    const menuOptionShowOnMap: MenuSelectOption = {
        disabled: !tableEntry.boundingBox,
        onSelect: menuAction(() => {
            tableEntry.boundingBox && onShowOnMap(tableEntry.boundingBox);
        }),
        name: t('publish.show-on-map'),
    };

    const menuOptions: MenuSelectOption[] = [
        ...conditionalMenuOption(tableEntry.publicationGroup, menuOptionMovePublicationGroupStage),
        menuOptionMoveStageChanges,
        menuOptionShowOnMap,
        ...conditionalMenuOption(!tableEntry.publicationGroup, menuOptionRevertSingleChange),
        ...conditionalMenuOption(tableEntry.publicationGroup, menuOptionPublicationGroupRevert),
        menuOptionRevertStageChanges,
    ];

    return (
        <React.Fragment>
            <tr className={'preview-table-item'}>
                <td>{tableEntry.uiName}</td>
                <td>{tableEntry.trackNumber ? tableEntry.trackNumber : ''}</td>
                <td>
                    {tableEntry.operation
                        ? t(`enum.publish-operation.${tableEntry.operation}`)
                        : ''}
                </td>
                <td>{formatDateFull(tableEntry.changeTime)}</td>
                <td>{tableEntry.userName}</td>
                {tableEntry.pendingValidation ? (
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
                        onClick={() =>
                            previewOperations.setPublicationStage.forSpecificChanges(
                                tableEntryAsPublishRequestIds,
                                moveTargetStage,
                            )
                        }
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
