import * as React from 'react';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { formatDateFull } from 'utils/date-utils';
import { useTranslation } from 'react-i18next';
import {
    PublicationStage,
    LayoutValidationIssue,
    PublicationValidationState,
} from 'publication/publication-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { ChangesBeingReverted, PreviewOperations } from 'preview/preview-view';
import {
    Menu,
    menuDivider,
    MenuOption,
    menuOption,
    MenuSelectOption,
} from 'vayla-design-lib/menu/menu';
import { PreviewTableEntry } from 'preview/preview-table';
import { BoundingBox } from 'model/geometry';
import { RevertRequestSource } from 'preview/preview-view-revert-request';
import { PublicationGroupAmounts } from 'publication/publication-utils';

import { ValidationStateCell } from './preview-table-validation-state-cell';
import { ValidationStateRow } from './preview-table-validation-state-row';

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
    validationState: PublicationValidationState;
    canRevertChanges: boolean;
};

export const PreviewTableItem: React.FC<PreviewTableItemProps> = ({
    tableEntry,
    publish = false,
    changesBeingReverted,
    previewOperations,
    publicationGroupAmounts,
    displayedTotalPublicationAssetAmount,
    onShowOnMap,
    validationState,
    canRevertChanges,
}) => {
    const { t } = useTranslation();
    const [isErrorRowExpanded, setIsErrorRowExpanded] = React.useState(false);
    const [actionMenuVisible, setActionMenuVisible] = React.useState(false);

    const issuesToStrings = (list: LayoutValidationIssue[], type: 'ERROR' | 'WARNING') => {
        const filtered = list.filter((e) => e.type === type);
        return filtered.map((error) => t(error.localizationKey, error.params));
    };

    const hasErrors = tableEntry.issues.length > 0;

    const errorTexts = issuesToStrings(tableEntry.issues, 'ERROR');
    const warningTexts = issuesToStrings(tableEntry.issues, 'WARNING');

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

    const menuOptionMoveAllShownChanges: MenuSelectOption = menuOption(
        () =>
            previewOperations.setPublicationStage.forAllShownChanges(
                displayedPublicationStage,
                moveTargetStage,
            ),
        t('publish.move-all-shown-changes', {
            amount: displayedTotalPublicationAssetAmount,
        }),
        'preview-move-all-shown-changes',
    );

    const menuOptionMovePublicationGroupStage: MenuSelectOption = menuOption(
        () => {
            if (tableEntry.publicationGroup) {
                previewOperations.setPublicationStage.forPublicationGroup(
                    tableEntry.publicationGroup,
                    moveTargetStage,
                );
            }
        },
        t('publish.move-publication-group', {
            amount: publicationGroupAssetAmount,
        }),
        `preview-move-publication-group-${tableEntry.publicationGroup?.id ?? 'unknown'}`,
    );

    const menuOptionRevertSingleChange: MenuSelectOption = menuOption(
        () =>
            previewOperations.revert.changesWithDependencies(
                [tableEntry.publishCandidate],
                tableEntryAsRevertRequestSource,
            ),
        t('publish.revert-change'),
        'preview-revert-change',
    );

    const menuOptionRevertAllShownChanges: MenuSelectOption = menuOption(
        () => {
            previewOperations.revert.stageChanges(
                displayedPublicationStage,
                tableEntryAsRevertRequestSource,
            );
        },
        t('publish.revert-all-shown-changes', {
            amount: displayedTotalPublicationAssetAmount,
        }),
        'preview-revert-all-shown-changes',
    );

    const menuOptionPublicationGroupRevert: MenuSelectOption = menuOption(
        () => {
            if (tableEntry.publicationGroup) {
                previewOperations.revert.publicationGroup(
                    tableEntry.publicationGroup,
                    tableEntryAsRevertRequestSource,
                );
            }
        },
        t('publish.revert-publication-group', {
            amount: publicationGroupAssetAmount,
        }),
        'preview-revert-publication-group',
    );

    const menuOptionShowOnMap: MenuSelectOption = menuOption(
        () => {
            tableEntry.boundingBox && onShowOnMap(tableEntry.boundingBox);
        },
        t('publish.show-on-map'),
        'preview-show-on-map',
        !tableEntry.boundingBox,
    );

    const menuOptions: MenuOption[] = [
        ...conditionalMenuOption(tableEntry.publicationGroup, menuOptionMovePublicationGroupStage),
        menuOptionMoveAllShownChanges,
        menuDivider(),
        menuOptionShowOnMap,
        menuDivider(),
        ...(canRevertChanges
            ? [
                  ...conditionalMenuOption(
                      !tableEntry.publicationGroup,
                      menuOptionRevertSingleChange,
                  ),
                  ...conditionalMenuOption(
                      tableEntry.publicationGroup,
                      menuOptionPublicationGroupRevert,
                  ),
                  menuOptionRevertAllShownChanges,
              ]
            : []),
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
                <ValidationStateCell
                    validationState={validationState}
                    hasErrors={hasErrors}
                    errorTexts={errorTexts}
                    warningTexts={warningTexts}
                    toggleRowExpansion={() => setIsErrorRowExpanded(!isErrorRowExpanded)}
                />
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
                <ValidationStateRow errorTexts={errorTexts} warningTexts={warningTexts} />
            )}
            {actionMenuVisible && (
                <Menu
                    positionRef={actionMenuRef}
                    items={menuOptions}
                    onClickOutside={() => setActionMenuVisible(false)}
                    onClose={() => setActionMenuVisible(false)}
                />
            )}
        </React.Fragment>
    );
};
