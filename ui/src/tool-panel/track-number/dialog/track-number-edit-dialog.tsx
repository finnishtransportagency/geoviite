import * as React from 'react';
import { Dialog, DialogVariant, DialogWidth } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { useTranslation } from 'react-i18next';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { createTrackNumber, updateTrackNumber } from 'track-layout/layout-track-number-api';
import {
    getSaveDisabledReasons,
    useReferenceLineStartAndEnd,
    useTrackNumberReferenceLine,
    useTrackNumbersIncludingDeleted,
} from 'track-layout/track-layout-react-utils';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import {
    actions,
    getErrors,
    initialTrackNumberEditState,
    reducer,
    TrackNumberSaveRequest,
} from './track-number-edit-store';
import { createDelegatesWithDispatcher } from 'store/store-utils';
import { FieldValidationIssueType } from 'utils/validation-utils';
import {
    LayoutReferenceLine,
    LayoutTrackNumber,
    LayoutTrackNumberId,
} from 'track-layout/track-layout-model';
import { formatTrackMeter } from 'utils/geography-utils';
import { Precision, roundToPrecision } from 'utils/rounding';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { layoutStates } from 'utils/enum-localization-utils';
import styles from 'geoviite-design-lib/dialog/dialog.scss';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import TrackNumberRevertConfirmationDialog from 'tool-panel/track-number/dialog/track-number-revert-confirmation-dialog';
import { Link } from 'vayla-design-lib/link/link';
import { onRequestDeleteTrackNumber } from 'tool-panel/track-number/track-number-deletion';
import { ChangesBeingReverted } from 'preview/preview-view';
import { isEqualIgnoreCase } from 'utils/string-utils';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { draftLayoutContext, LayoutContext } from 'common/common-model';

type TrackNumberEditDialogContainerProps = {
    editTrackNumberId?: LayoutTrackNumberId;
    onClose: () => void;
    onSave?: (trackNumberId: LayoutTrackNumberId) => void;
};

type TrackNumberEditDialogProps = {
    layoutContext: LayoutContext;
    inEditTrackNumber?: LayoutTrackNumber;
    inEditReferenceLine?: LayoutReferenceLine;
    isNewDraft: boolean;
    trackNumbers: LayoutTrackNumber[];
    onClose: () => void;
    onSave?: (trackNumberId: LayoutTrackNumberId) => void;
    onEditTrackNumber: (trackNumberId: LayoutTrackNumberId) => void;
};

export const TrackNumberEditDialogContainer: React.FC<TrackNumberEditDialogContainerProps> = ({
    editTrackNumberId,
    onClose,
    onSave,
}: TrackNumberEditDialogContainerProps) => {
    const layoutContext = draftLayoutContext(
        useTrackLayoutAppSelector((state) => state.layoutContext),
    );
    const trackNumbers = useTrackNumbersIncludingDeleted(layoutContext);
    const [trackNumberId, setTrackNumberId] = React.useState<LayoutTrackNumberId | undefined>(
        editTrackNumberId,
    );
    const editReferenceLine = useTrackNumberReferenceLine(trackNumberId, layoutContext);
    const isNewDraft = !!editReferenceLine && !editReferenceLine.hasOfficial;

    if (trackNumbers !== undefined && trackNumberId === editReferenceLine?.trackNumberId) {
        return (
            <TrackNumberEditDialog
                layoutContext={layoutContext}
                inEditTrackNumber={trackNumbers.find((tn) => tn.id === trackNumberId)}
                inEditReferenceLine={editReferenceLine}
                trackNumbers={trackNumbers}
                isNewDraft={isNewDraft}
                onClose={onClose}
                onSave={onSave}
                onEditTrackNumber={setTrackNumberId}
            />
        );
    } else {
        return <div />;
    }
};

const mapError = (errorReason: string) => `track-number-edit.error.${errorReason}`;

export const TrackNumberEditDialog: React.FC<TrackNumberEditDialogProps> = ({
    layoutContext,
    inEditTrackNumber,
    inEditReferenceLine,
    trackNumbers,
    isNewDraft,
    onClose,
    onSave,
    onEditTrackNumber,
}: TrackNumberEditDialogProps) => {
    const { t } = useTranslation();

    const [state, dispatcher] = React.useReducer(
        reducer,
        initialTrackNumberEditState(inEditTrackNumber, inEditReferenceLine, trackNumbers),
    );
    const stateActions = createDelegatesWithDispatcher(dispatcher, actions);
    const startAndEndPoints = inEditReferenceLine
        ? useReferenceLineStartAndEnd(inEditReferenceLine.id, draftLayoutContext(layoutContext))
        : undefined;

    const [saveInProgress, setSaveInProgress] = React.useState<boolean>(false);
    const [deletingDraft, setDeletingDraft] = React.useState<ChangesBeingReverted>();
    const [nonDraftDeleteConfirmationVisible, setNonDraftDeleteConfirmationVisible] =
        React.useState<boolean>(false);

    const canSetDeleted = inEditTrackNumber !== undefined && !isNewDraft;
    const trackNumberStateOptions = layoutStates
        .map((s) => (s.value !== 'DELETED' || canSetDeleted ? s : { ...s, disabled: true }))
        .map((s) => ({ ...s, qaId: s.value }));

    const confirmNewDraftDelete = () => {
        inEditTrackNumber &&
            onRequestDeleteTrackNumber(layoutContext.branch, inEditTrackNumber, setDeletingDraft);
    };

    const saveOrConfirm = () => {
        if (state.request?.state === 'DELETED' && inEditTrackNumber?.state !== 'DELETED') {
            setNonDraftDeleteConfirmationVisible(true);
        } else {
            saveTrackNumber();
        }
    };

    const saveTrackNumber = () => {
        setSaveInProgress(true);
        const operation = inEditTrackNumber
            ? updateTrackNumber(layoutContext, inEditTrackNumber.id, state.request)
            : createTrackNumber(layoutContext, state.request);
        operation
            .then((tn) => {
                if (tn) {
                    Snackbar.success('track-number-edit.result.succeeded');
                    if (onSave) onSave(tn);
                    onClose();
                } else {
                    Snackbar.error('track-number-edit.result.failed');
                }
            })
            .finally(() => {
                setNonDraftDeleteConfirmationVisible(false);
                setSaveInProgress(false);
            });
    };

    const hasErrors =
        state.validationIssues.filter((e) => e.type === FieldValidationIssueType.ERROR).length > 0;
    const numberErrors = getErrors(state, 'number');
    const stateErrors = getErrors(state, 'state');
    const descriptionErrors = getErrors(state, 'description');
    const startAddressErrors = getErrors(state, 'startAddress');

    const otherTrackNumber = trackNumbers.find(
        (tn) =>
            isEqualIgnoreCase(tn.number, state.request.number) && tn.id !== inEditTrackNumber?.id,
    );

    const moveToEditLinkText = (tn: LayoutTrackNumber) => {
        const state = tn.state === 'DELETED' ? ` (${t('enum.LayoutState.DELETED')})` : '';
        return t('track-number-edit.action.move-to-edit', {
            number: tn.number + state,
        });
    };

    return (
        <React.Fragment>
            <Dialog
                title={t(
                    inEditTrackNumber
                        ? 'track-number-edit.title.edit'
                        : 'track-number-edit.title.create',
                )}
                onClose={onClose}
                width={DialogWidth.TWO_COLUMNS}
                footerContent={
                    <React.Fragment>
                        {inEditTrackNumber && (
                            <div className={styles['dialog__footer-content--left-aligned']}>
                                <Button
                                    disabled={!inEditTrackNumber.isDraft}
                                    onClick={() => {
                                        inEditTrackNumber && confirmNewDraftDelete();
                                    }}
                                    variant={ButtonVariant.WARNING}>
                                    {t('button.revert-draft')}
                                </Button>
                            </div>
                        )}
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                disabled={saveInProgress}
                                onClick={onClose}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                disabled={hasErrors || saveInProgress}
                                isProcessing={saveInProgress}
                                qa-id="save-track-number-changes"
                                onClick={saveOrConfirm}
                                title={getSaveDisabledReasons(
                                    state.validationIssues.map((e) => e.reason),
                                    saveInProgress,
                                )
                                    .map((reason) => t(mapError(reason)))
                                    .join(', ')}>
                                {t('track-number-edit.action.save')}
                            </Button>
                        </div>
                    </React.Fragment>
                }>
                <FormLayout isProcessing={saveInProgress} dualColumn>
                    <FormLayoutColumn>
                        <Heading size={HeadingSize.SUB}>
                            {t('track-number-edit.title.track-number')}
                        </Heading>
                        <FieldLayout
                            label={`${t('track-number-edit.field.number')} *`}
                            value={
                                <TextField
                                    qa-id="track-number-name"
                                    value={state.request.number}
                                    onChange={(e) =>
                                        stateActions.onUpdateProp({
                                            key: 'number' as keyof TrackNumberSaveRequest,
                                            value: e.target.value,
                                            editingExistingValue: !!inEditTrackNumber,
                                        })
                                    }
                                    onBlur={() => stateActions.onCommitField('number')}
                                    disabled={saveInProgress}
                                    hasError={numberErrors.length > 0}
                                    wide
                                />
                            }
                            errors={numberErrors.map(({ reason }) => t(mapError(reason)))}>
                            {otherTrackNumber && (
                                <Link
                                    className={dialogStyles['dialog__alert']}
                                    onClick={() => onEditTrackNumber(otherTrackNumber.id)}>
                                    {moveToEditLinkText(otherTrackNumber)}
                                </Link>
                            )}
                        </FieldLayout>
                        <FieldLayout
                            label={`${t('track-number-edit.field.state')} *`}
                            value={
                                <Dropdown
                                    qaId="track-number-state"
                                    value={state.request.state}
                                    canUnselect={false}
                                    options={trackNumberStateOptions}
                                    onChange={(state) =>
                                        stateActions.onUpdateProp({
                                            key: 'state' as keyof TrackNumberSaveRequest,
                                            value: state || 'NOT_IN_USE',
                                            editingExistingValue: !!inEditTrackNumber,
                                        })
                                    }
                                    onBlur={() => stateActions.onCommitField('state')}
                                    hasError={stateErrors.length > 0}
                                    wide
                                    searchable
                                />
                            }
                            errors={stateErrors.map(({ reason }) => t(mapError(reason)))}
                        />
                        <FieldLayout
                            label={`${t('track-number-edit.field.description')} *`}
                            value={
                                <TextField
                                    qa-id="track-number-description"
                                    value={state.request.description}
                                    onChange={(e) =>
                                        stateActions.onUpdateProp({
                                            key: 'description' as keyof TrackNumberSaveRequest,
                                            value: e.target.value,
                                            editingExistingValue: !!inEditTrackNumber,
                                        })
                                    }
                                    onBlur={() => stateActions.onCommitField('description')}
                                    disabled={saveInProgress}
                                    hasError={descriptionErrors.length > 0}
                                    wide
                                />
                            }
                            errors={descriptionErrors.map(({ reason }) => t(mapError(reason)))}
                        />
                    </FormLayoutColumn>
                    <FormLayoutColumn>
                        <Heading size={HeadingSize.SUB}>
                            {t('track-number-edit.title.reference-line')}
                        </Heading>
                        <FieldLayout
                            label={`${t('track-number-edit.field.start-location')} *`}
                            value={
                                <TextField
                                    value={state.request.startAddress}
                                    onChange={(e) =>
                                        stateActions.onUpdateProp({
                                            key: 'startAddress' as keyof TrackNumberSaveRequest,
                                            value: e.target.value,
                                            editingExistingValue: !!inEditTrackNumber,
                                        })
                                    }
                                    onBlur={() => stateActions.onCommitField('startAddress')}
                                    disabled={saveInProgress}
                                    hasError={startAddressErrors.length > 0}
                                    wide
                                />
                            }
                            errors={startAddressErrors.map(({ reason }) => t(mapError(reason)))}
                        />
                        {startAndEndPoints && (
                            <FieldLayout
                                label={t('track-number-edit.field.end-location')}
                                value={
                                    <TextField
                                        value={
                                            startAndEndPoints?.end?.address
                                                ? formatTrackMeter(startAndEndPoints.end.address)
                                                : '-'
                                        }
                                        wide
                                        disabled
                                    />
                                }
                            />
                        )}
                        {inEditReferenceLine && (
                            <FieldLayout
                                label={t('track-number-edit.field.true-length')}
                                value={
                                    <TextField
                                        value={roundToPrecision(
                                            inEditReferenceLine.length,
                                            Precision.alignmentLengthMeters,
                                        )}
                                        wide
                                        disabled
                                    />
                                }
                            />
                        )}
                    </FormLayoutColumn>
                </FormLayout>
            </Dialog>
            {nonDraftDeleteConfirmationVisible && (
                <Dialog
                    title={t('track-number-edit.title.delete-non-draft')}
                    variant={DialogVariant.DARK}
                    allowClose={false}
                    footerContent={
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                onClick={() => setNonDraftDeleteConfirmationVisible(false)}
                                variant={ButtonVariant.SECONDARY}
                                disabled={saveInProgress}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                disabled={saveInProgress}
                                isProcessing={saveInProgress}
                                variant={ButtonVariant.PRIMARY_WARNING}
                                onClick={saveTrackNumber}>
                                {t('button.delete')}
                            </Button>
                        </div>
                    }>
                    <div className={'dialog__text'}>
                        {t('track-number-edit.dialog.deleted-track-numbers-not-allowed')}
                    </div>
                    <div className={'dialog__text'}>
                        {t('track-number-edit.dialog.confirm-track-number-delete')}
                    </div>
                </Dialog>
            )}
            {inEditTrackNumber && deletingDraft && (
                <TrackNumberRevertConfirmationDialog
                    layoutContext={layoutContext}
                    changesBeingReverted={deletingDraft}
                    onClose={() => setDeletingDraft(undefined)}
                    onSave={() => {
                        inEditTrackNumber && onSave && onSave(inEditTrackNumber.id);
                        onClose();
                    }}
                />
            )}
        </React.Fragment>
    );
};

export default TrackNumberEditDialog;
