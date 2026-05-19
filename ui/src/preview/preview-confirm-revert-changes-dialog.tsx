import { Dialog, DialogHeight, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import * as React from 'react';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import { ChangesBeingReverted } from 'preview/preview-view';
import {
    PublicationRequestDependencyList,
    publicationRequestTypeTranslationKey,
} from 'preview/publication-request-dependency-list';
import { getChangeTimes } from 'common/change-time-api';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { RevertRequestType } from 'preview/preview-view-revert-request';
import { LayoutContext } from 'common/common-model';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';

export interface PreviewRejectConfirmDialogProps {
    layoutContext: LayoutContext;
    changesBeingReverted: ChangesBeingReverted;
    setChangesBeingReverted: (changes: ChangesBeingReverted) => void;
    confirmRevertChanges: () => void;
    cancelRevertChanges: () => void;
}

export const PreviewConfirmRevertChangesDialog: React.FC<PreviewRejectConfirmDialogProps> = ({
    layoutContext,
    changesBeingReverted,
    setChangesBeingReverted,
    cancelRevertChanges,
    confirmRevertChanges,
}) => {
    const { t } = useTranslation();
    const [isReverting, setIsReverting] = React.useState(false);

    const revertType = changesBeingReverted.requestedRevertChange.type;

    const defaultIncludeSplits =
        revertType === RevertRequestType.STAGE_CHANGES_WITH_PARTIAL_SPLITS &&
        changesBeingReverted.requestedRevertChange.nonSplitCandidates.length === 0;
    const [includeSplits, setIncludeSplits] = React.useState(defaultIncludeSplits);

    const dialogTitle = (): string => {
        switch (revertType) {
            case RevertRequestType.STAGE_CHANGES:
                return t('publish.revert-confirm.title.stage-changes');

            case RevertRequestType.STAGE_CHANGES_WITH_PARTIAL_SPLITS:
                return t('publish.revert-confirm.title.stage-changes-with-partial-splits');

            case RevertRequestType.CHANGES_WITH_DEPENDENCIES:
                return t('publish.revert-confirm.title.changes-with-dependencies');

            case RevertRequestType.PUBLICATION_GROUP:
                return t('publish.revert-confirm.title.publication-group');

            default:
                return exhaustiveMatchingGuard(revertType);
        }
    };

    const dialogQuestion = (): string => {
        switch (revertType) {
            case RevertRequestType.STAGE_CHANGES: {
                return t('publish.revert-confirm.description.stage-changes', {
                    amount: changesBeingReverted.requestedRevertChange.amount,
                });
            }

            case RevertRequestType.STAGE_CHANGES_WITH_PARTIAL_SPLITS: {
                return t('publish.revert-confirm.description.stage-changes-with-partial-splits');
            }

            case RevertRequestType.CHANGES_WITH_DEPENDENCIES: {
                const generalDescription = t(
                    'publish.revert-confirm.description.changes-with-dependencies',
                );
                const typeDescription = t(
                    `publish.revert-confirm.revert-target.${publicationRequestTypeTranslationKey(
                        changesBeingReverted.requestedRevertChange.source.type,
                    )}`,
                );
                const itemDescription = changesBeingReverted.requestedRevertChange.source.name;

                return `${generalDescription} ${typeDescription} ${itemDescription}?`;
            }

            case RevertRequestType.PUBLICATION_GROUP: {
                return t('publish.revert-confirm.description.publication-group', {
                    amount: changesBeingReverted.requestedRevertChange.amount,
                });
            }

            default: {
                return exhaustiveMatchingGuard(revertType);
            }
        }
    };

    const handleIncludeSplitsChange = (checked: boolean) => {
        setIncludeSplits(checked);
        if (revertType === RevertRequestType.STAGE_CHANGES_WITH_PARTIAL_SPLITS) {
            const { nonSplitCandidates, fullSplitCandidates } =
                changesBeingReverted.requestedRevertChange;
            setChangesBeingReverted({
                ...changesBeingReverted,
                changeIncludingDependencies: checked
                    ? [...nonSplitCandidates, ...fullSplitCandidates]
                    : nonSplitCandidates,
            });
        }
    };

    const handleConfirm = () => {
        setIsReverting(true);
        confirmRevertChanges();
    };

    return (
        <Dialog
            title={dialogTitle()}
            variant={DialogVariant.LIGHT}
            height={DialogHeight.RESTRICTED_TO_HALF_OF_VIEWPORT}
            allowClose={!isReverting}
            onClose={cancelRevertChanges}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button
                        onClick={cancelRevertChanges}
                        disabled={isReverting}
                        variant={ButtonVariant.SECONDARY}>
                        {t('publish.revert-confirm.cancel')}
                    </Button>
                    <Button
                        icon={Icons.Delete}
                        disabled={isReverting}
                        isProcessing={isReverting}
                        variant={ButtonVariant.WARNING}
                        onClick={handleConfirm}>
                        {t('publish.revert-confirm.confirm')}
                    </Button>
                </div>
            }>
            <div>{dialogQuestion()}</div>
            {revertType === RevertRequestType.STAGE_CHANGES_WITH_PARTIAL_SPLITS && (
                <React.Fragment>
                    <p>
                        <Checkbox
                            checked={includeSplits}
                            onChange={(e) => handleIncludeSplitsChange(e.target.checked)}>
                            {t('publish.revert-confirm.include-splits-checkbox', {
                                amount: changesBeingReverted.requestedRevertChange
                                    .fullSplitCandidates.length,
                            })}
                        </Checkbox>
                    </p>
                    <p>
                        {t('publish.revert-confirm.selected-changes', {
                            amount: changesBeingReverted.changeIncludingDependencies.length,
                        })}
                    </p>
                </React.Fragment>
            )}
            <PublicationRequestDependencyList
                layoutContext={layoutContext}
                changeTimes={getChangeTimes()}
                changesBeingReverted={changesBeingReverted}
            />
        </Dialog>
    );
};
