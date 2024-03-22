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
import { ChangesBeingReverted, PreviewOperations } from 'preview/preview-view';
import {
    Menu,
    menuDividerOption,
    MenuOption,
    menuSelectOption,
    MenuSelectOption,
} from 'vayla-design-lib/menu/menu';
import { PreviewSelectType, PreviewTableEntry, PublishableObjectId } from 'preview/preview-table';
import { BoundingBox } from 'model/geometry';
import { RevertRequestSource } from 'preview/preview-view-revert-request';
import { PublicationAssetChangeAmounts } from 'preview/preview-view-data';
import { brand } from 'common/brand';

const createPublishRequestIdsFromTableEntry = (
    id: PublishableObjectId,
    type: PreviewSelectType,
): PublishRequestIds => ({
    trackNumbers: type === PreviewSelectType.trackNumber ? [brand(id)] : [],
    referenceLines: type === PreviewSelectType.referenceLine ? [brand(id)] : [],
    locationTracks: type === PreviewSelectType.locationTrack ? [brand(id)] : [],
    switches: type === PreviewSelectType.switch ? [brand(id)] : [],
    kmPosts: type === PreviewSelectType.kmPost ? [brand(id)] : [],
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

    const menuOptionMoveStageChanges: MenuSelectOption = menuSelectOption(
        menuAction(() =>
            previewOperations.setPublicationStage.forAllStageChanges(
                displayedPublicationStage,
                moveTargetStage,
            ),
        ),
        t('publish.move-stage-changes', {
            amount: stagePublicationAssetAmount,
        }),
        'preview-move-stage-changes',
    );

    const menuOptionMovePublicationGroupStage: MenuSelectOption = menuSelectOption(
        menuAction(() => {
            if (tableEntry.publicationGroup) {
                previewOperations.setPublicationStage.forPublicationGroup(
                    tableEntry.publicationGroup,
                    moveTargetStage,
                );
            }
        }),
        t('publish.move-publication-group', {
            amount: publicationGroupAssetAmount,
        }),
        'preview-move-publication-group',
    );

    const menuOptionRevertSingleChange: MenuSelectOption = menuSelectOption(
        menuAction(() =>
            previewOperations.revert.changesWithDependencies(
                tableEntryAsPublishRequestIds,
                tableEntryAsRevertRequestSource,
            ),
        ),
        t('publish.revert-change'),
        'preview-revert-change',
    );

    const menuOptionRevertStageChanges: MenuSelectOption = menuSelectOption(
        menuAction(() => {
            previewOperations.revert.stageChanges(
                displayedPublicationStage,
                tableEntryAsRevertRequestSource,
            );
        }),
        t('publish.revert-stage-changes', {
            amount: stagePublicationAssetAmount,
        }),
        'preview-revert-stage-changes',
    );

    const menuOptionPublicationGroupRevert: MenuSelectOption = menuSelectOption(
        menuAction(() => {
            if (tableEntry.publicationGroup) {
                previewOperations.revert.publicationGroup(
                    tableEntry.publicationGroup,
                    tableEntryAsRevertRequestSource,
                );
            }
        }),
        t('publish.revert-publication-group', {
            amount: publicationGroupAssetAmount,
        }),
        'preview-revert-publication-group',
    );

    const menuOptionShowOnMap: MenuSelectOption = menuSelectOption(
        menuAction(() => {
            tableEntry.boundingBox && onShowOnMap(tableEntry.boundingBox);
        }),
        t('publish.show-on-map'),
        'preview-show-on-map',
        !tableEntry.boundingBox,
    );

    const menuOptions: MenuOption<never>[] = [
        ...conditionalMenuOption(tableEntry.publicationGroup, menuOptionMovePublicationGroupStage),
        menuOptionMoveStageChanges,
        menuDividerOption(),
        menuOptionShowOnMap,
        menuDividerOption(),
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
