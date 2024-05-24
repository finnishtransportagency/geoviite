import * as React from 'react';
import styles from './preview-view.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { formatDateFull } from 'utils/date-utils';
import { useTranslation } from 'react-i18next';
import { PublicationStage, LayoutValidationIssue } from 'publication/publication-model';
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
import { PreviewTableEntry } from 'preview/preview-table';
import { BoundingBox } from 'model/geometry';
import { RevertRequestSource } from 'preview/preview-view-revert-request';
import { PublicationGroupAmounts } from 'publication/publication-utils';

const conditionalMenuOption = (
    condition: unknown | undefined,
    menuOption: MenuSelectOption,
): MenuSelectOption[] => (condition ? [menuOption] : []);

export type PreviewTableItemProps = {
    tableEntry: PreviewTableEntry;
    publish?: boolean;
    changesBeingReverted: ChangesBeingReverted | undefined;
    previewOperations: PreviewOperations;
    publicationGroupAmounts: PublicationGroupAmounts;
    displayedTotalPublicationAssetAmount: number;
    onShowOnMap: (bbox: BoundingBox) => void;
    isValidating: (item: PreviewTableEntry) => boolean;
};

export const PreviewTableItem: React.FC<PreviewTableItemProps> = ({
    tableEntry,
    publish = false,
    changesBeingReverted,
    previewOperations,
    publicationGroupAmounts,
    displayedTotalPublicationAssetAmount,
    onShowOnMap,
    isValidating,
}) => {
    const { t } = useTranslation();
    const [isErrorRowExpanded, setIsErrorRowExpanded] = React.useState(false);
    const [actionMenuVisible, setActionMenuVisible] = React.useState(false);

    const issuesToStrings = (list: LayoutValidationIssue[], type: 'ERROR' | 'WARNING') => {
        const filtered = list.filter((e) => e.type === type);
        return filtered.map((error) => t(error.localizationKey, error.params));
    };
    const errorTexts = issuesToStrings(tableEntry.issues, 'ERROR');
    const warningTexts = issuesToStrings(tableEntry.issues, 'WARNING');
    const hasErrors = tableEntry.issues.length > 0;

    const statusCellClassName = createClassName(
        styles['preview-table-item__status-cell'],
        hasErrors && styles['preview-table-item__status-cell--expandable'],
    );

    const actionMenuRef = React.useRef(null);

    const publicationGroupAssetAmount = tableEntry.publicationGroup
        ? publicationGroupAmounts[tableEntry.publicationGroup?.id]
        : undefined;

    const [displayedPublicationStage, moveTargetStage] = publish
        ? [PublicationStage.STAGED, PublicationStage.UNSTAGED]
        : [PublicationStage.UNSTAGED, PublicationStage.STAGED];

    const tableEntryAsRevertRequestSource: RevertRequestSource = {
        id: tableEntry.id,
        type: tableEntry.type,
        name: tableEntry.name,
    };

    const menuAction = (menuActionFunction: () => void) => (): void => {
        menuActionFunction();
        setActionMenuVisible(false);
    };

    const menuOptionMoveAllShownChanges: MenuSelectOption = menuSelectOption(
        menuAction(() =>
            previewOperations.setPublicationStage.forAllShownChanges(
                displayedPublicationStage,
                moveTargetStage,
            ),
        ),
        t('publish.move-all-shown-changes', {
            amount: displayedTotalPublicationAssetAmount,
        }),
        'preview-move-all-shown-changes',
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
        `preview-move-publication-group-${tableEntry.publicationGroup?.id ?? 'unknown'}`,
    );

    const menuOptionRevertSingleChange: MenuSelectOption = menuSelectOption(
        menuAction(() =>
            previewOperations.revert.changesWithDependencies(
                [tableEntry.publishCandidate],
                tableEntryAsRevertRequestSource,
            ),
        ),
        t('publish.revert-change'),
        'preview-revert-change',
    );

    const menuOptionRevertAllShownChanges: MenuSelectOption = menuSelectOption(
        menuAction(() => {
            previewOperations.revert.stageChanges(
                displayedPublicationStage,
                tableEntryAsRevertRequestSource,
            );
        }),
        t('publish.revert-all-shown-changes', {
            amount: displayedTotalPublicationAssetAmount,
        }),
        'preview-revert-all-shown-changes',
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
        menuOptionMoveAllShownChanges,
        menuDividerOption(),
        menuOptionShowOnMap,
        menuDividerOption(),
        ...conditionalMenuOption(!tableEntry.publicationGroup, menuOptionRevertSingleChange),
        ...conditionalMenuOption(tableEntry.publicationGroup, menuOptionPublicationGroupRevert),
        menuOptionRevertAllShownChanges,
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
                {isValidating(tableEntry) ? (
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
                                [tableEntry.publishCandidate],
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
