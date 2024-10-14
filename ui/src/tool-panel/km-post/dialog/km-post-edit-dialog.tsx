import React from 'react';
import { Dialog, DialogVariant, DialogWidth } from 'geoviite-design-lib/dialog/dialog';
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
    kmPostSaveRequest,
    reducer,
} from 'tool-panel/km-post/dialog/km-post-edit-store';
import { KmPostEditFields } from 'linking/linking-model';
import {
    getKmPost,
    getKmPostByNumber,
    insertKmPost,
    updateKmPost,
} from 'track-layout/layout-km-post-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { LayoutKmPost, LayoutKmPostId, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { useDebouncedState } from 'utils/react-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import KmPostDeleteConfirmationDialog from 'tool-panel/km-post/dialog/km-post-delete-confirmation-dialog';
import { Link } from 'vayla-design-lib/link/link';
import {
    getSaveDisabledReasons,
    useTrackNumbersIncludingDeleted,
} from 'track-layout/track-layout-react-utils';
import { draftLayoutContext, LayoutContext, Srid } from 'common/common-model';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { KmPostEditDialogGkLocationSection } from 'tool-panel/km-post/dialog/km-post-edit-dialog-gk-location-section';
import { GeometryPoint } from 'model/geometry';

export type KmPostEditDialogType = 'MODIFY' | 'CREATE' | 'LINKING';

type KmPostEditDialogContainerProps = {
    kmPostId?: LayoutKmPostId;
    onClose: () => void;
    onSave?: (kmPostId: LayoutKmPostId) => void;
    prefilledTrackNumberId?: LayoutTrackNumberId;
    geometryKmPostGkLocation?: GeometryPoint;
    editType: KmPostEditDialogType;
    geometryPlanSrid?: Srid;
};

type KmPostEditDialogProps = {
    layoutContext: LayoutContext;
    kmPostId?: LayoutKmPostId;
    onClose: () => void;
    onSave?: (kmPostId: LayoutKmPostId) => void;
    prefilledTrackNumberId?: LayoutTrackNumberId;
    onEditKmPost: (id?: LayoutKmPostId) => void;
    geometryKmPostGkLocation?: GeometryPoint;
    geometryKmPostLocation?: GeometryPoint;
    editType: KmPostEditDialogType;
    geometryPlanSrid?: Srid;
};

export const KmPostEditDialogContainer: React.FC<KmPostEditDialogContainerProps> = (
    props: KmPostEditDialogContainerProps,
) => {
    const [editKmPostId, setEditKmPostId] = React.useState<LayoutKmPostId | undefined>(
        props.kmPostId,
    );
    const layoutContext = useTrackLayoutAppSelector((state) => state.layoutContext);
    return (
        <KmPostEditDialog
            layoutContext={layoutContext}
            kmPostId={editKmPostId}
            onClose={props.onClose}
            onSave={props.onSave}
            onEditKmPost={setEditKmPostId}
            prefilledTrackNumberId={props.prefilledTrackNumberId}
            geometryKmPostGkLocation={props.geometryKmPostGkLocation}
            editType={props.editType}
            geometryPlanSrid={props.geometryPlanSrid}
        />
    );
};

export const KmPostEditDialog: React.FC<KmPostEditDialogProps> = (props: KmPostEditDialogProps) => {
    const { t } = useTranslation();
    const [state, dispatcher] = React.useReducer(reducer, {
        ...initialKmPostEditState,
        gkLocationEnabled: !!props.geometryKmPostGkLocation,
    });
    const stateActions = createDelegatesWithDispatcher(dispatcher, actions);
    const kmPostStateOptions = layoutStates
        .filter((ls) => !state.isNewKmPost || ls.value != 'DELETED')
        .map((ls) => ({ ...ls, qaId: ls.value }));

    const debouncedKmNumber = useDebouncedState(state.kmPost?.kmNumber, 300);
    const firstInputRef = React.useRef<HTMLInputElement>(null);
    const [nonDraftDeleteConfirmationVisible, setNonDraftDeleteConfirmationVisible] =
        React.useState<boolean>(state.kmPost?.state == 'DELETED');
    const [draftDeleteConfirmationVisible, setDraftDeleteConfirmationVisible] =
        React.useState<boolean>();
    const trackNumbers = useTrackNumbersIncludingDeleted(
        draftLayoutContext(props.layoutContext),
        undefined,
    );

    // Load an existing kmPost or create a new KmPost
    React.useEffect(() => {
        if (props.kmPostId) {
            stateActions.init();
            getKmPost(props.kmPostId, draftLayoutContext(props.layoutContext))
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
            stateActions.initWithNewKmPost({
                trackNumberId: props.prefilledTrackNumberId,
                geometryKmPostLocation: props.geometryKmPostGkLocation,
            });
            firstInputRef.current?.focus();
        }
    }, [props.kmPostId]);

    React.useEffect(() => {
        findTrackNumberKmPost(draftLayoutContext(props.layoutContext), state).then((found) =>
            stateActions.onTrackNumberKmPostFound(found),
        );
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
            return insertKmPost(
                draftLayoutContext(props.layoutContext),
                kmPostSaveRequest(state),
            ).then(
                (kmPostId) => {
                    Snackbar.success('km-post-dialog.insert-succeeded');
                    return kmPostId;
                },
                () => void Snackbar.error('km-post-dialog.insert-failed'),
            );
        } else if (state.existingKmPost) {
            return updateKmPost(
                draftLayoutContext(props.layoutContext),
                state.existingKmPost.id,
                kmPostSaveRequest(state),
            ).then(
                (kmPostId) => {
                    Snackbar.success('km-post-dialog.modify-succeeded');
                    return kmPostId;
                },
                () => void Snackbar.error('km-post-dialog.modify-failed'),
            );
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

    function updateProp<TKey extends keyof KmPostEditFields>(
        key: TKey,
        value: KmPostEditFields[TKey],
    ) {
        stateActions.onUpdateProp({
            key: key,
            value: value,
            editingExistingValue: !state.isNewKmPost,
        });
    }

    function getVisibleErrorsByProp(prop: keyof KmPostEditFields) {
        return state.allFieldsCommitted || state.committedFields.includes(prop)
            ? state.validationIssues
                  .filter((issue) => issue.field == prop)
                  .map((issue) => t(`km-post-dialog.${issue.reason}`))
            : [];
    }

    function hasErrors(prop: keyof KmPostEditFields) {
        return getVisibleErrorsByProp(prop).length > 0;
    }

    const trackNumberOptions = (trackNumbers ?? [])
        .filter((tn) => tn.id === state.existingKmPost?.trackNumberId || tn.state !== 'DELETED')
        .map((tn) => {
            const note = tn.state === 'DELETED' ? ` (${t('enum.layout-state.DELETED')})` : '';
            return { name: tn.number + note, value: tn.id, qaId: `track-number-${tn.id}` };
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
                onClose={close}
                width={DialogWidth.TWO_COLUMNS}
                footerContent={
                    <React.Fragment>
                        {state.existingKmPost?.editState === 'CREATED' && !state.isNewKmPost && (
                            <div className={dialogStyles['dialog__footer-content--left-aligned']}>
                                <Button
                                    onClick={() =>
                                        props.kmPostId
                                            ? setDraftDeleteConfirmationVisible(true)
                                            : undefined
                                    }
                                    icon={Icons.Delete}
                                    variant={ButtonVariant.WARNING}>
                                    {t('button.delete-draft')}
                                </Button>
                            </div>
                        )}
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                disabled={state.isSaving}
                                onClick={close}>
                                {t('button.cancel')}
                            </Button>
                            <span onClick={() => stateActions.validate()}>
                                <Button
                                    qa-id="save-km-post-changes"
                                    disabled={!canSaveKmPost(state)}
                                    isProcessing={state.isSaving}
                                    onClick={() => saveOrConfirm()}
                                    title={getSaveDisabledReasons(
                                        state.validationIssues.map((e) => e.reason),
                                        state.isSaving,
                                    )
                                        .map((reason) => t(`km-post-dialog.${reason}`))
                                        .join(', ')}>
                                    {t('button.save')}
                                </Button>
                            </span>
                        </div>
                    </React.Fragment>
                }>
                <FormLayout dualColumn>
                    <FormLayoutColumn>
                        <Heading size={HeadingSize.SUB}>
                            {t('km-post-dialog.general-title')}
                        </Heading>

                        <FieldLayout
                            label={`${t('km-post-dialog.km-post')} *`}
                            value={
                                <TextField
                                    qa-id="km-post-number"
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
                                        className={dialogStyles['dialog__alert']}
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
                                    qaId="km-post-state"
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
                    </FormLayoutColumn>
                    <FormLayoutColumn>
                        <KmPostEditDialogGkLocationSection
                            getVisibleErrorsByProp={getVisibleErrorsByProp}
                            hasErrors={hasErrors}
                            state={state}
                            stateActions={stateActions}
                            updateProp={updateProp}
                            geometryKmPostGkLocation={props.geometryKmPostGkLocation}
                            editType={props.editType}
                            geometryPlanSrid={props.geometryPlanSrid}
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
                                variant={ButtonVariant.SECONDARY}
                                onClick={() => setNonDraftDeleteConfirmationVisible(false)}>
                                {t('button.cancel')}
                            </Button>
                            <Button variant={ButtonVariant.PRIMARY_WARNING} onClick={save}>
                                {t('button.delete')}
                            </Button>
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
                    layoutContext={draftLayoutContext(props.layoutContext)}
                    id={props.kmPostId}
                    onClose={() => setDraftDeleteConfirmationVisible(false)}
                    onSave={() => {
                        props.kmPostId && props.onSave && props.onSave(props.kmPostId);
                        props.onClose();
                    }}
                />
            )}
        </React.Fragment>
    );
};

function findTrackNumberKmPost(
    layoutContext: LayoutContext,
    state: KmPostEditState,
): Promise<LayoutKmPost | undefined> {
    if (state.kmPost?.trackNumberId !== undefined && isValidKmNumber(state.kmPost.kmNumber)) {
        return getKmPostByNumber(
            draftLayoutContext(layoutContext),
            state.kmPost.trackNumberId,
            state.kmPost.kmNumber,
            true,
        );
    } else {
        return Promise.resolve(undefined);
    }
}
