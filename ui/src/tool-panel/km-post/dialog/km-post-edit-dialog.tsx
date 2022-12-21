import React from 'react';
import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import { useTranslation } from 'react-i18next';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { layoutStates } from 'utils/enum-localization-utils';
import { createDelegates } from 'store/store-utils';
import {
    actions,
    canSaveKmPost,
    initialKmPostEditState,
    reducer,
} from 'tool-panel/km-post/dialog/km-post-edit-store';
import { KmPostSaveRequest } from 'linking/linking-model';
import {
    getKmPost,
    getKmPostByNumber,
    insertKmPost,
    updateKmPost,
} from 'track-layout/layout-km-post-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { LayoutKmPost, LayoutKmPostId } from 'track-layout/track-layout-model';
import { GeometryTrackNumberId } from 'geometry/geometry-model';
import { isNullOrBlank } from 'utils/string-utils';
import { useDebouncedState } from 'utils/react-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import styles from 'vayla-design-lib/dialog/dialog.scss';
import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';
import KmPostDeleteConfirmationDialog from 'tool-panel/km-post/dialog/km-post-delete-confirmation-dialog';
import { createClassName } from 'vayla-design-lib/utils';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';

export type KmPostDialogProps = {
    kmPostId?: LayoutKmPostId;
    onClose?: () => void;
    onInsert?: (kmPostId: LayoutKmPostId) => void;
    onUpdate?: () => void;
    onUnselect?: () => void;
    prefilledTrackNumberId?: GeometryTrackNumberId;
};

export const KmPostEditDialog: React.FC<KmPostDialogProps> = (props: KmPostDialogProps) => {
    const { t } = useTranslation();
    const [state, dispatcher] = React.useReducer(reducer, initialKmPostEditState);
    const stateActions = createDelegates(dispatcher, actions);
    const kmPostStateOptions = layoutStates.filter(
        (ls) => !state.isNewKmPost || ls.value != 'DELETED',
    );
    const debouncedKmNumber = useDebouncedState(state.kmPost?.kmNumber, 300);
    const firstInputRef = React.useRef<HTMLInputElement>(null);
    const [officialKmPost, setOfficialKmPost] = React.useState<LayoutKmPost>();
    const [nonDraftDeleteConfirmationVisible, setNonDraftDeleteConfirmationVisible] =
        React.useState<boolean>(state.kmPost?.state == 'DELETED');
    const [draftDeleteConfirmationVisible, setDraftDeleteConfirmationVisible] =
        React.useState<boolean>();

    const closeNonDraftDeleteConfirmation = () => {
        setNonDraftDeleteConfirmationVisible(false);
    };

    const confirmNonDraftDraftDelete = () => {
        setDraftDeleteConfirmationVisible(true);
    };

    const closeDraftDeleteConfirmation = () => {
        setDraftDeleteConfirmationVisible(false);
    };

    const onKmPostDeleted = () => {
        closeNonDraftDeleteConfirmation();
        props.onClose && props.onClose();
        props.kmPostId && props.onUnselect && props.onUnselect();
    };

    // Load track numbers once
    React.useEffect(() => {
        stateActions.onStartLoadingTrackNumbers();
        getTrackNumbers('DRAFT').then((trackNumbers) => {
            stateActions.onTrackNumbersLoaded(trackNumbers);
        });
    }, []);

    // Load an existing kmPost or create a new KmPost
    React.useEffect(() => {
        if (props.kmPostId) {
            stateActions.onStartLoadingKmPost();
            getKmPost(props.kmPostId, 'DRAFT').then((kmPost) => {
                if (kmPost) {
                    stateActions.onKmPostLoaded(kmPost);
                    firstInputRef.current?.focus();
                } else {
                    Snackbar.error(t('km-post-dialog.cant-open-deleted'));
                    onKmPostDeleted();
                }
            });
        } else {
            stateActions.initWithNewKmPost(props.prefilledTrackNumberId);
            firstInputRef.current?.focus();
        }
    }, [props.kmPostId]);

    React.useEffect(() => {
        if (
            !hasErrors('kmNumber') &&
            state.kmPost?.trackNumberId &&
            !isNullOrBlank(state.kmPost.kmNumber) &&
            (state.kmPost.kmNumber != state.baselineKmNumber ||
                state.kmPost.trackNumberId != state.baselineTrackNumberId)
        ) {
            getKmPostByNumber('DRAFT', state.kmPost.trackNumberId, state.kmPost.kmNumber).then(
                (found) => {
                    if (found) {
                        stateActions.onKmNumberExistsOnTrack();
                    }
                },
            );
        }
    }, [state.kmPost?.trackNumberId, debouncedKmNumber, state.kmPost?.state]);

    React.useEffect(() => {
        if (props.kmPostId) {
            getKmPost(props.kmPostId, 'OFFICIAL').then((kmPost) => {
                if (kmPost) setOfficialKmPost(kmPost);
            });
        }
    }, []);

    function cancelSave() {
        props.onClose && props.onClose();
    }

    const saveOrConfirm = () => {
        if (state.kmPost?.state === 'DELETED') {
            setNonDraftDeleteConfirmationVisible(true);
        } else {
            save();
        }
    };

    function save() {
        if (canSaveKmPost(state) && state.kmPost) {
            stateActions.onStartSaving();
            if (state.isNewKmPost) {
                insertKmPost(state.kmPost)
                    .then((result) => {
                        result
                            .map((kmPostId) => {
                                stateActions.onSaveSucceed(kmPostId);
                                props.onInsert && props.onInsert(kmPostId);
                                Snackbar.success(t('km-post-dialog.created-successfully'));
                            })
                            .mapErr((_err) => {
                                stateActions.onSaveFailed();
                                Snackbar.error(t('km-post-dialog.adding-failed'));
                            });
                    })
                    .catch(() => {
                        stateActions.onSaveFailed();
                        Snackbar.error(t('km-post-dialog.adding-failed'));
                    });
            } else if (state.existingKmPost) {
                updateKmPost(state.existingKmPost.id, state.kmPost)
                    .then((result) => {
                        result
                            .map((kmPostId) => {
                                stateActions.onSaveSucceed(kmPostId);
                                props.onUpdate && props.onUpdate();
                                const successMessage = t('km-post-dialog.modified-successfully');
                                Snackbar.success(successMessage);
                            })
                            .mapErr((_err) => {
                                stateActions.onSaveFailed();
                            });
                    })
                    .catch(() => {
                        stateActions.onSaveFailed();
                    });
            }
        }
    }

    function updateProp<TKey extends keyof KmPostSaveRequest>(
        key: TKey,
        value: KmPostSaveRequest[TKey],
    ) {
        stateActions.onUpdateProp({
            key: key,
            value: value,
            editingExistingValue: !state.isNewKmPost,
        });
    }

    function getVisibleErrorsByProp(prop: keyof KmPostSaveRequest) {
        return state.allFieldsCommitted || state.committedFields.includes(prop)
            ? state.validationErrors
                  .filter((error) => error.field == prop)
                  .map((error) => t(`km-post-dialog.${error.reason}`))
            : [];
    }

    function hasErrors(prop: keyof KmPostSaveRequest) {
        return getVisibleErrorsByProp(prop).length > 0;
    }

    return (
        <div>
            <Dialog
                title={
                    state.isNewKmPost
                        ? t('km-post-dialog.title-new')
                        : t('km-post-dialog.title-edit')
                }
                onClose={() => cancelSave()}
                className={dialogStyles['dialog--ultrawide']}
                scrollable={false}
                footerClassName={'dialog-footer'}
                footerContent={
                    <React.Fragment>
                        <div className={styles['dialog-footer__content-area']}>
                            <div className={styles['dialog-footer__content--shrink']}>
                                {officialKmPost?.draftType === 'NEW_DRAFT' && !state.isNewKmPost && (
                                    <Button
                                        onClick={() =>
                                            props.kmPostId
                                                ? confirmNonDraftDraftDelete()
                                                : undefined
                                        }
                                        icon={Icons.Delete}
                                        variant={ButtonVariant.WARNING}>
                                        {t('km-post-dialog.delete-draft')}
                                    </Button>
                                )}
                            </div>
                            <div
                                className={createClassName(
                                    styles['dialog-footer__content--grow'],
                                    styles['dialog-footer__content--centered'],
                                    styles['dialog-footer__content--padded'],
                                )}>
                                <Button
                                    variant={ButtonVariant.SECONDARY}
                                    disabled={state.isSaving}
                                    onClick={() => cancelSave()}>
                                    {t('button.return')}
                                </Button>
                                <span onClick={() => stateActions.validate()}>
                                    <Button
                                        disabled={!canSaveKmPost(state)}
                                        isProcessing={state.isSaving}
                                        onClick={() => saveOrConfirm()}>
                                        {t('button.save')}
                                    </Button>
                                </span>
                            </div>
                        </div>
                    </React.Fragment>
                }>
                <FormLayout>
                    <FormLayoutColumn>
                        <Heading size={HeadingSize.SUB}>
                            {t('km-post-dialog.general-title')}
                        </Heading>

                        <FieldLayout
                            label={`${t('km-post-dialog.km-post')} *`}
                            value={
                                <TextField
                                    value={state.kmPost?.kmNumber}
                                    onChange={(e) => updateProp('kmNumber', e.target.value)}
                                    onBlur={() => stateActions.onCommitField('kmNumber')}
                                    hasError={hasErrors('kmNumber')}
                                    ref={firstInputRef}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp('kmNumber')}
                        />
                        <FieldLayout
                            label={`${t('km-post-dialog.track-number')} *`}
                            value={
                                <Dropdown
                                    value={state.kmPost?.trackNumberId}
                                    options={state.trackNumbers.map((trackNumber) => ({
                                        name: trackNumber.number,
                                        value: trackNumber.id,
                                    }))}
                                    onChange={(value) => updateProp('trackNumberId', value)}
                                    onBlur={() => stateActions.onCommitField('trackNumberId')}
                                    hasError={hasErrors('trackNumberId')}
                                    wide
                                    searchable
                                    disabled={
                                        props.prefilledTrackNumberId != undefined ||
                                        props.kmPostId != undefined
                                    }
                                />
                            }
                            errors={getVisibleErrorsByProp('trackNumberId')}
                        />
                        <FieldLayout
                            label={`${t('km-post-dialog.state')} *`}
                            value={
                                <Dropdown
                                    value={state.kmPost?.state}
                                    options={kmPostStateOptions}
                                    onChange={(value) => value && updateProp('state', value)}
                                    onBlur={() => stateActions.onCommitField('state')}
                                    hasError={hasErrors('state')}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp('state')}
                        />
                        <Heading size={HeadingSize.SUB}>
                            {t('km-post-dialog.extra-info-heading')}
                        </Heading>
                        <FieldLayout
                            label={t('km-post-dialog.owner')}
                            value={
                                <Dropdown
                                    value={undefined}
                                    options={[]}
                                    onChange={(_value) => undefined}
                                    onBlur={() => undefined}
                                    wide
                                    disabled
                                />
                            }
                        />
                    </FormLayoutColumn>
                    <FormLayoutColumn>
                        <Heading size={HeadingSize.SUB}>
                            {t('km-post-dialog.info-from-linking')}
                        </Heading>
                        <FieldLayout
                            label={t('km-post-dialog.location')}
                            value={
                                <TextField
                                    value={'-'}
                                    onChange={(_value) => undefined}
                                    onBlur={() => undefined}
                                    wide
                                    disabled
                                />
                            }
                        />
                        <FieldLayout
                            label={t('km-post-dialog.coordinate-system')}
                            value={
                                <TextField
                                    value={'-'}
                                    onChange={(_value) => undefined}
                                    onBlur={() => undefined}
                                    wide
                                    disabled
                                />
                            }
                        />
                        <FieldLayout
                            label={t('km-post-dialog.coordinates')}
                            value={
                                <TextField
                                    value={'-'}
                                    onChange={(_value) => undefined}
                                    onBlur={() => undefined}
                                    wide
                                    disabled
                                />
                            }
                        />
                    </FormLayoutColumn>
                </FormLayout>
                {nonDraftDeleteConfirmationVisible && (
                    <Dialog
                        title={t('km-post-delete-dialog.title')}
                        variant={DialogVariant.DARK}
                        allowClose={false}
                        className={dialogStyles['dialog--normal']}
                        footerContent={
                            <React.Fragment>
                                <Button
                                    onClick={closeNonDraftDeleteConfirmation}
                                    variant={ButtonVariant.SECONDARY}>
                                    {t('button.cancel')}
                                </Button>
                                <Button onClick={save}>{t('button.delete')}</Button>
                            </React.Fragment>
                        }>
                        <div className={'dialog__text'}>
                            {t('km-post-delete-dialog.deleted-km-posts-not-allowed')}
                        </div>
                        <div className={'dialog__text'}>
                            {t('km-post-delete-dialog.confirm-km-post-delete')}
                        </div>
                    </Dialog>
                )}
                {props.kmPostId && draftDeleteConfirmationVisible && (
                    <KmPostDeleteConfirmationDialog
                        id={props.kmPostId}
                        onCancel={closeDraftDeleteConfirmation}
                        onClose={onKmPostDeleted}
                    />
                )}
            </Dialog>
        </div>
    );
};
