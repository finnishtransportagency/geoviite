import React from 'react';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { useTranslation } from 'react-i18next';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { layoutStates } from 'utils/enum-localization-utils';
import { createDelegatesWithDispatcher } from 'store/store-utils';
import {
    actions,
    canSaveKmPost,
    initialKmPostEditState,
    isValidKmNumber,
    KmPostEditState,
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
import { useDebouncedState } from 'utils/react-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import KmPostDeleteConfirmationDialog from 'tool-panel/km-post/dialog/km-post-delete-confirmation-dialog';
import { Link } from 'vayla-design-lib/link/link';
import { useTrackNumbersIncludingDeleted } from 'track-layout/track-layout-react-utils';

type KmPostEditDialogContainerProps = {
    kmPostId?: LayoutKmPostId;
    onClose: () => void;
    onSave?: (kmPostId: LayoutKmPostId) => void;
    prefilledTrackNumberId?: GeometryTrackNumberId;
};

type KmPostEditDialogProps = {
    kmPostId?: LayoutKmPostId;
    onClose: () => void;
    onSave?: (kmPostId: LayoutKmPostId) => void;
    prefilledTrackNumberId?: GeometryTrackNumberId;
    onEditKmPost: (id?: LayoutKmPostId) => void;
};

export const KmPostEditDialogContainer: React.FC<KmPostEditDialogContainerProps> = (
    props: KmPostEditDialogContainerProps,
) => {
    const [editKmPostId, setEditKmPostId] = React.useState<LayoutKmPostId | undefined>(
        props.kmPostId,
    );
    return (
        <KmPostEditDialog
            kmPostId={editKmPostId}
            onClose={props.onClose}
            onSave={props.onSave}
            onEditKmPost={setEditKmPostId}
            prefilledTrackNumberId={props.prefilledTrackNumberId}
        />
    );
};

export const KmPostEditDialog: React.FC<KmPostEditDialogProps> = (props: KmPostEditDialogProps) => {
    const { t } = useTranslation();
    const [state, dispatcher] = React.useReducer(reducer, initialKmPostEditState);
    const stateActions = createDelegatesWithDispatcher(dispatcher, actions);
    const kmPostStateOptions = layoutStates.filter(
        (ls) => !state.isNewKmPost || ls.value != 'DELETED',
    );
    const debouncedKmNumber = useDebouncedState(state.kmPost?.kmNumber, 300);
    const firstInputRef = React.useRef<HTMLInputElement>(null);
    const [nonDraftDeleteConfirmationVisible, setNonDraftDeleteConfirmationVisible] =
        React.useState<boolean>(state.kmPost?.state == 'DELETED');
    const [draftDeleteConfirmationVisible, setDraftDeleteConfirmationVisible] =
        React.useState<boolean>();
    const trackNumbers = useTrackNumbersIncludingDeleted('DRAFT', undefined);

    // Load an existing kmPost or create a new KmPost
    React.useEffect(() => {
        if (props.kmPostId) {
            stateActions.init();
            getKmPost(props.kmPostId, 'DRAFT')
                .then((kmPost) => {
                    if (kmPost) {
                        stateActions.onKmPostLoaded(kmPost);
                        firstInputRef.current?.focus();
                    } else {
                        Snackbar.error('km-post-dialog.cant-open-deleted');
                        close();
                    }
                })
                .catch(() => close());
        } else {
            stateActions.initWithNewKmPost(props.prefilledTrackNumberId);
            firstInputRef.current?.focus();
        }
    }, [props.kmPostId]);

    React.useEffect(() => {
        findTrackNumberKmPost(state).then((found) => stateActions.onTrackNumberKmPostFound(found));
    }, [state.kmPost?.trackNumberId, debouncedKmNumber, state.kmPost?.state, state.existingKmPost]);

    const close = () => {
        setNonDraftDeleteConfirmationVisible(false);
        props.onClose();
    };

    const saveOrConfirm = () => {
        if (state.kmPost?.state === 'DELETED' && state.existingKmPost?.state !== 'DELETED') {
            setNonDraftDeleteConfirmationVisible(true);
        } else {
            save();
        }
    };

    async function saveState(state: KmPostEditState): Promise<LayoutKmPostId | undefined> {
        if (state.isNewKmPost) {
            const result = await insertKmPost(state.kmPost);
            if (result.isOk()) Snackbar.success('km-post-dialog.insert-succeeded');
            else Snackbar.error('km-post-dialog.insert-failed');
            return result.unwrapOr(undefined);
        } else if (state.existingKmPost) {
            const result = await updateKmPost(state.existingKmPost.id, state.kmPost);
            if (result.isOk()) Snackbar.success('km-post-dialog.modify-succeeded');
            else Snackbar.error('km-post-dialog.modify-failed');
            return result.unwrapOr(undefined);
        } else {
            return Promise.resolve(undefined);
        }
    }
    function save() {
        if (canSaveKmPost(state) && state.kmPost) {
            stateActions.onStartSaving();
            saveState(state).then((kmPostId) => {
                if (kmPostId) {
                    props.onSave && props.onSave(kmPostId);
                    props.onClose();
                }
                stateActions.onEndSaving();
            });
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

    const trackNumberOptions = (trackNumbers ?? [])
        .filter((tn) => tn.id === state.existingKmPost?.trackNumberId || tn.state !== 'DELETED')
        .map((tn) => {
            const note = tn.state === 'DELETED' ? ` (${t('enum.layout-state.DELETED')})` : '';
            return { name: tn.number + note, value: tn.id };
        });

    const moveToEditLinkText = (kmp: LayoutKmPost) => {
        const state = kmp.state === 'DELETED' ? ` (${t('enum.layout-state.DELETED')})` : '';
        return t('km-post-dialog.move-to-edit', {
            number: kmp.kmNumber + state,
        });
    };

    return (
        <React.Fragment>
            <Dialog
                title={
                    state.isNewKmPost
                        ? t('km-post-dialog.title-new')
                        : t('km-post-dialog.title-edit')
                }
                onClose={() => close()}
                footerContent={
                    <React.Fragment>
                        {state.existingKmPost?.draftType === 'NEW_DRAFT' && !state.isNewKmPost && (
                            <Button
                                onClick={() =>
                                    props.kmPostId
                                        ? setDraftDeleteConfirmationVisible(true)
                                        : undefined
                                }
                                icon={Icons.Delete}
                                variant={ButtonVariant.WARNING}>
                                {t('button.delete')}
                            </Button>
                        )}
                        <div
                            className={
                                state.existingKmPost?.draftType === 'NEW_DRAFT'
                                    ? dialogStyles['dialog__footer-content--right-aligned']
                                    : dialogStyles['dialog__footer-content--centered']
                            }>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                disabled={state.isSaving}
                                onClick={() => close()}>
                                {t('button.cancel')}
                            </Button>
                            <span onClick={() => stateActions.validate()}>
                                <Button
                                    qa-id="save-km-post-changes"
                                    disabled={!canSaveKmPost(state)}
                                    isProcessing={state.isSaving}
                                    onClick={() => saveOrConfirm()}>
                                    {t('button.save')}
                                </Button>
                            </span>
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
                            errors={getVisibleErrorsByProp('kmNumber')}>
                            {state.trackNumberKmPost &&
                                state.trackNumberKmPost.id !== state.existingKmPost?.id && (
                                    <Link
                                        className="move-to-edit-link"
                                        onClick={() =>
                                            props.onEditKmPost(state.trackNumberKmPost?.id)
                                        }>
                                        {moveToEditLinkText(state.trackNumberKmPost)}
                                    </Link>
                                )}
                        </FieldLayout>
                        <FieldLayout
                            label={`${t('km-post-dialog.track-number')} *`}
                            value={
                                <Dropdown
                                    value={state.kmPost?.trackNumberId}
                                    options={trackNumberOptions}
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
                </FormLayout>
            </Dialog>
            {nonDraftDeleteConfirmationVisible && (
                <Dialog
                    title={t('km-post-delete-dialog.title')}
                    variant={DialogVariant.DARK}
                    allowClose={false}
                    footerContent={
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                onClick={() => setNonDraftDeleteConfirmationVisible(false)}
                                variant={ButtonVariant.SECONDARY}>
                                {t('button.cancel')}
                            </Button>
                            <Button onClick={save}>{t('button.delete')}</Button>
                        </div>
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
                    onClose={() => setDraftDeleteConfirmationVisible(false)}
                    onSave={props.onSave}
                />
            )}
        </React.Fragment>
    );
};

function findTrackNumberKmPost(state: KmPostEditState): Promise<LayoutKmPost | undefined> {
    if (state.kmPost?.trackNumberId !== undefined && isValidKmNumber(state.kmPost.kmNumber)) {
        return getKmPostByNumber('DRAFT', state.kmPost.trackNumberId, state.kmPost.kmNumber, true);
    } else {
        return Promise.resolve(undefined);
    }
}
