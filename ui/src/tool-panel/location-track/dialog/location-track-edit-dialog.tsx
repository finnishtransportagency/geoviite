import React from 'react';
import {
    LayoutLocationTrack,
    LocationTrackDescription,
    LocationTrackDescriptionSuffixMode,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { Dialog, DialogVariant, DialogWidth } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { LocationTrackSaveRequest } from 'linking/linking-model';
import {
    getLocationTrack,
    getLocationTrackDescriptions,
    getLocationTracksBySearchTerm,
    insertLocationTrack,
    updateLocationTrack,
} from 'track-layout/layout-location-track-api';
import {
    actions,
    canSaveLocationTrack,
    initialLocationTrackEditState,
    isProcessing,
    reducer,
    setVaylavirastoOwnerIdFrom,
} from 'tool-panel/location-track/dialog/location-track-edit-store';
import { createDelegatesWithDispatcher } from 'store/store-utils';
import { Dropdown, Item } from 'vayla-design-lib/dropdown/dropdown';
import {
    locationTrackStates,
    locationTrackTypes,
    topologicalConnectivityTypes,
} from 'utils/enum-localization-utils';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { useTranslation } from 'react-i18next';
import {
    getSaveDisabledReasons,
    useConflictingTracks,
    useLocationTrack,
    useLocationTrackInfoboxExtras,
    useLocationTrackStartAndEnd,
} from 'track-layout/track-layout-react-utils';
import { formatTrackMeter } from 'utils/geography-utils';
import { Precision, roundToPrecision } from 'utils/rounding';
import LocationTrackDeleteConfirmationDialog from 'tool-panel/location-track/location-track-delete-confirmation-dialog';
import { debounceAsync } from 'utils/async-utils';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import styles from './location-track-edit-dialog.scss';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { exhaustiveMatchingGuard, ifDefined } from 'utils/type-utils';
import { DescriptionSuffixDropdown } from 'tool-panel/location-track/description-suffix-dropdown';
import { getLocationTrackOwners } from 'common/common-api';
import { useLoader } from 'utils/react-utils';
import { Link } from 'vayla-design-lib/link/link';
import { ChangeTimes } from 'common/common-slice';
import { getChangeTimes } from 'common/change-time-api';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { first } from 'utils/array-utils';
import { draftLayoutContext, LayoutContext } from 'common/common-model';

type LocationTrackDialogContainerProps = {
    locationTrackId?: LocationTrackId;
    onClose: () => void;
    onSave?: (locationTrackId: LocationTrackId) => void;
};

export const LocationTrackEditDialogContainer: React.FC<LocationTrackDialogContainerProps> = (
    props: LocationTrackDialogContainerProps,
) => {
    const [editTrackId, setEditTrackId] = React.useState<LocationTrackId | undefined>(
        props.locationTrackId,
    );

    const layoutContext = useTrackLayoutAppSelector((state) => state.layoutContext);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const locationTrack = useLocationTrack(
        editTrackId,
        draftLayoutContext(layoutContext),
        changeTimes.layoutLocationTrack,
    );
    return (
        <LocationTrackEditDialog
            layoutContext={layoutContext}
            locationTrack={locationTrack}
            onClose={props.onClose}
            onSave={props.onSave}
            changeTimes={getChangeTimes()}
            onEditTrack={(id) => setEditTrackId(id)}
        />
    );
};

type LocationTrackDialogProps = {
    layoutContext: LayoutContext;
    locationTrack?: LayoutLocationTrack;
    onClose: () => void;
    onSave?: (locationTrackId: LocationTrackId) => void;
    changeTimes: ChangeTimes;
    onEditTrack: (id: LocationTrackId) => void;
};

const debouncedSearchTracks = debounceAsync(getLocationTracksBySearchTerm, 250);

export const LocationTrackEditDialog: React.FC<LocationTrackDialogProps> = (
    props: LocationTrackDialogProps,
) => {
    const { t } = useTranslation();

    const firstInputRef = React.useRef<HTMLInputElement>(null);
    const [state, dispatcher] = React.useReducer(reducer, initialLocationTrackEditState);
    const [selectedDuplicateTrack, setSelectedDuplicateTrack] = React.useState<
        LayoutLocationTrack | undefined
    >(undefined);
    const [nonDraftDeleteConfirmationVisible, setNonDraftDeleteConfirmationVisible] =
        React.useState<boolean>(state.locationTrack?.state == 'DELETED');
    const [draftDeleteConfirmationVisible, setDraftDeleteConfirmationVisible] =
        React.useState<boolean>();
    const [locationTrackDescriptionSuffixMode, setLocationTrackDescriptionSuffixMode] =
        React.useState<LocationTrackDescriptionSuffixMode>();
    const stateActions = createDelegatesWithDispatcher(dispatcher, actions);
    const layoutContextDraft = draftLayoutContext(props.layoutContext);

    React.useEffect(() => {
        setLocationTrackDescriptionSuffixMode(state.locationTrack?.descriptionSuffix);
    }, [state.locationTrack]);
    const [startAndEndPoints, _] = useLocationTrackStartAndEnd(
        state.existingLocationTrack?.id,
        layoutContextDraft,
        props.changeTimes,
    );

    const [extraInfo] = useLocationTrackInfoboxExtras(
        props.locationTrack?.id,
        layoutContextDraft,
        props.changeTimes,
    );

    const stateOptions = locationTrackStates
        .filter(
            (ls) =>
                (!state.isNewLocationTrack &&
                    state.existingLocationTrack?.editState !== 'CREATED') ||
                ls.value != 'DELETED',
        )
        .map((ls) => ({ ...ls, qaId: ls.value }));

    const typeOptions = locationTrackTypes.map((ls) => ({ ...ls, qaId: ls.value }));
    const topologicalConnectivityOptions = topologicalConnectivityTypes.map((tc) => ({
        ...tc,
        qaId: tc.value,
    }));

    const locationTrackOwners = useLoader(getLocationTrackOwners, []);

    const duplicate = useLocationTrack(
        props.locationTrack?.duplicateOf,
        layoutContextDraft,
        props.changeTimes.layoutLocationTrack,
    );
    React.useEffect(() => {
        if (duplicate && !selectedDuplicateTrack) setSelectedDuplicateTrack(duplicate);
    }, [duplicate]);

    const validTrackName = !state.validationIssues.some((e) => e.field === 'name')
        ? state.locationTrack.name
        : '';
    const trackWithSameName = ifDefined(
        useConflictingTracks(
            state.locationTrack.trackNumberId,
            [validTrackName],
            props.locationTrack?.id ? [props.locationTrack.id] : [],
            layoutContextDraft,
        ),
        first,
    );
    React.useEffect(() => {
        if (
            locationTrackOwners !== undefined &&
            state.isNewLocationTrack &&
            !state.locationTrack.ownerId
        ) {
            setVaylavirastoOwnerIdFrom(locationTrackOwners, (id) => updateProp('ownerId', id));
        }
    }, [locationTrackOwners, state.isNewLocationTrack]);

    // Load track numbers once
    React.useEffect(() => {
        stateActions.onStartLoadingTrackNumbers();
        getTrackNumbers(layoutContextDraft, undefined, true).then((trackNumbers) => {
            stateActions.onTrackNumbersLoaded(trackNumbers);
        });
    }, []);

    // Load an existing location track or create new one
    React.useEffect(() => {
        if (props.locationTrack) {
            stateActions.onStartLoadingLocationTrack();
            getLocationTrack(
                props.locationTrack.id,
                layoutContextDraft,
                props.changeTimes.layoutLocationTrack,
            ).then((locationTrack) => {
                if (locationTrack) {
                    stateActions.onLocationTrackLoaded(locationTrack);
                    firstInputRef.current?.focus();
                } else {
                    Snackbar.error('location-track-dialog.cant-open-deleted');
                    props.onClose();
                }
            });
        } else {
            stateActions.initWithNewLocationTrack(locationTrackOwners);
            firstInputRef.current?.focus();
        }
    }, [props.locationTrack?.id]);

    function cancelSave() {
        props.onClose && props.onClose();
    }

    const saveOrConfirm = () => {
        if (
            state.locationTrack?.state === 'DELETED' &&
            state.existingLocationTrack?.state !== 'DELETED'
        ) {
            setNonDraftDeleteConfirmationVisible(true);
        } else {
            save();
        }
    };

    function save() {
        if (canSaveLocationTrack(state) && state.locationTrack) {
            stateActions.onStartSaving();
            if (state.isNewLocationTrack) {
                insertLocationTrack(layoutContextDraft, state.locationTrack)
                    .then((locationTrackId) => {
                        props.onSave && props.onSave(locationTrackId);
                        Snackbar.success('location-track-dialog.created-successfully');
                        props.onClose();
                    })
                    .finally(() => stateActions.onEndSaving());
            } else if (state.existingLocationTrack) {
                updateLocationTrack(
                    layoutContextDraft,
                    state.existingLocationTrack.id,
                    state.locationTrack,
                )
                    .then((locationTrackId) => {
                        props.onSave && props.onSave(locationTrackId);
                        const successMessage =
                            state.locationTrack?.state === 'DELETED'
                                ? 'location-track-dialog.deleted-successfully'
                                : 'location-track-dialog.modified-successfully';
                        Snackbar.success(successMessage);
                        props.onClose();
                    })
                    .finally(() => stateActions.onEndSaving());
            }
        }
    }

    function updateProp<TKey extends keyof LocationTrackSaveRequest>(
        key: TKey,
        value: LocationTrackSaveRequest[TKey],
    ) {
        stateActions.onUpdateProp({
            key: key,
            value: value,
            editingExistingValue: !state.isNewLocationTrack,
        });
    }

    function getVisibleErrorsByProp(prop: keyof LocationTrackSaveRequest) {
        return state.allFieldsCommitted || state.committedFields.includes(prop)
            ? state.validationIssues
                  .filter((issue) => issue.field == prop)
                  .map((issue) => t(`location-track-dialog.${issue.reason}`))
            : [];
    }

    function hasErrors(prop: keyof LocationTrackSaveRequest) {
        return getVisibleErrorsByProp(prop).length > 0;
    }

    // Use memoized function to make debouncing functionality to work when re-rendering
    const getDuplicateTrackOptions = React.useCallback(
        (searchTerm: string) =>
            findDuplicateTrackOptions(layoutContextDraft, props.locationTrack?.id, searchTerm),
        [layoutContextDraft.branch, props.locationTrack?.id],
    );

    function onDuplicateTrackSelected(duplicateTrack: LocationTrackItemValue | undefined) {
        updateProp('duplicateOf', duplicateTrack?.locationTrack?.id);
        setSelectedDuplicateTrack(duplicateTrack?.locationTrack);
    }

    const switchToSwitchDescriptionSuffix = () =>
        `${shortenSwitchName(extraInfo?.switchAtStart?.name) ?? '???'} - ${
            shortenSwitchName(extraInfo?.switchAtEnd?.name) ?? '???'
        }`;

    const switchToBufferDescriptionSuffix = () =>
        `${
            shortenSwitchName(extraInfo?.switchAtStart?.name) ??
            shortenSwitchName(extraInfo?.switchAtEnd?.name) ??
            '???'
        } - ${t('location-track-dialog.buffer')}`;

    const switchToOwnershipBoundaryDescriptionSuffix = () =>
        `${
            shortenSwitchName(extraInfo?.switchAtStart?.name) ??
            shortenSwitchName(extraInfo?.switchAtEnd?.name) ??
            '???'
        } - ${t('location-track-dialog.ownership-boundary')}`;

    const descriptionSuffix = (mode: LocationTrackDescriptionSuffixMode) => {
        switch (mode) {
            case 'NONE':
                return undefined;
            case 'SWITCH_TO_SWITCH':
                return switchToSwitchDescriptionSuffix();
            case 'SWITCH_TO_BUFFER':
                return switchToBufferDescriptionSuffix();
            case 'SWITCH_TO_OWNERSHIP_BOUNDARY':
                return switchToOwnershipBoundaryDescriptionSuffix();
            default:
                return exhaustiveMatchingGuard(mode);
        }
    };

    const fullDescription = () => {
        const base = state.locationTrack?.descriptionBase ?? '';
        const suffix = descriptionSuffix(locationTrackDescriptionSuffixMode ?? 'NONE');
        return suffix ? `${base} ${suffix}` : base;
    };

    const shortenSwitchName = (name?: string) => {
        const splits = (name && name.split(' ')) ?? '';
        const last = splits.length ? splits[splits.length - 1] : '';
        const numberPart = last?.[0] === 'V' ? last.substring(1) : '';
        const number = parseInt(numberPart, 10);
        return !isNaN(number) ? `V${number.toString(10).padStart(3, '0')}` : undefined;
    };
    const trackNumberOptions = state.trackNumbers
        .filter(
            (tn) => tn.id === state.existingLocationTrack?.trackNumberId || tn.state !== 'DELETED',
        )
        .map((tn) => {
            const note =
                tn.state === 'DELETED' ? ` (${t('enum.location-track-state.DELETED')})` : '';
            return { name: tn.number + note, value: tn.id, qaId: `track-number-${tn.id}` };
        });

    const moveToEditLinkText = (track: LayoutLocationTrack) => {
        return track.state === 'DELETED'
            ? t('location-track-dialog.move-to-edit-deleted')
            : t('location-track-dialog.move-to-edit', { name: track.name });
    };

    const manuallySetDuplicates =
        extraInfo?.duplicates?.filter(
            (d) =>
                state.existingLocationTrack?.id &&
                d.duplicateStatus.duplicateOfId === state.existingLocationTrack?.id,
        ) || [];

    return (
        <React.Fragment>
            <Dialog
                title={
                    state.isNewLocationTrack
                        ? t('location-track-dialog.title-new')
                        : t('location-track-dialog.title-edit')
                }
                onClose={() => cancelSave()}
                width={DialogWidth.TWO_COLUMNS}
                footerContent={
                    <React.Fragment>
                        {state.existingLocationTrack?.editState === 'CREATED' &&
                            !state.isNewLocationTrack && (
                                <div
                                    className={
                                        dialogStyles['dialog__footer-content--left-aligned']
                                    }>
                                    <Button
                                        onClick={() =>
                                            state.existingLocationTrack &&
                                            setDraftDeleteConfirmationVisible(true)
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
                                onClick={() => cancelSave()}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                disabled={!canSaveLocationTrack(state)}
                                isProcessing={state.isSaving}
                                onClick={() => {
                                    stateActions.validate();
                                    saveOrConfirm();
                                }}
                                title={getSaveDisabledReasons(
                                    state.validationIssues.map((e) => e.reason),
                                    state.isSaving,
                                )
                                    .map((reason) => t(`location-track-dialog.${reason}`))
                                    .join(', ')}>
                                {t('button.save')}
                            </Button>
                        </div>
                    </React.Fragment>
                }>
                <FormLayout isProcessing={isProcessing(state)} dualColumn>
                    <FormLayoutColumn>
                        <Heading size={HeadingSize.SUB}>
                            {t('location-track-dialog.basic-info-heading')}
                        </Heading>

                        <FieldLayout
                            label={`${t('location-track-dialog.track-logo')} *`}
                            value={
                                <TextField
                                    qa-id="location-track-name"
                                    value={state.locationTrack?.name}
                                    onChange={(e) => updateProp('name', e.target.value)}
                                    onBlur={() => stateActions.onCommitField('name')}
                                    hasError={hasErrors('name')}
                                    ref={firstInputRef}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp('name')}>
                            {trackWithSameName && (
                                <>
                                    <div
                                        className={
                                            styles['location-track-edit-dialog__alert-color']
                                        }>
                                        {trackWithSameName.state === 'DELETED'
                                            ? t('location-track-dialog.name-in-use-deleted')
                                            : t('location-track-dialog.name-in-use')}
                                    </div>
                                    <Link
                                        className={styles['location-track-edit-dialog__alert']}
                                        onClick={() => props.onEditTrack(trackWithSameName.id)}>
                                        {moveToEditLinkText(trackWithSameName)}
                                    </Link>
                                </>
                            )}
                        </FieldLayout>
                        <FieldLayout
                            label={`${t('location-track-dialog.track-number')} *`}
                            value={
                                <Dropdown
                                    qaId="location-track-track-number"
                                    value={state.locationTrack?.trackNumberId}
                                    options={trackNumberOptions}
                                    onChange={(value) => updateProp('trackNumberId', value)}
                                    onBlur={() => stateActions.onCommitField('trackNumberId')}
                                    hasError={hasErrors('trackNumberId')}
                                    wide
                                    searchable
                                />
                            }
                            errors={getVisibleErrorsByProp('trackNumberId')}
                        />
                        <FieldLayout
                            label={`${t('location-track-dialog.state')} *`}
                            value={
                                <Dropdown
                                    qaId="location-track-state"
                                    value={state.locationTrack?.state}
                                    options={stateOptions}
                                    onChange={(value) => value && updateProp('state', value)}
                                    onBlur={() => stateActions.onCommitField('state')}
                                    hasError={hasErrors('state')}
                                    wide
                                    searchable
                                />
                            }
                            errors={getVisibleErrorsByProp('state')}
                        />
                        <FieldLayout
                            label={`${t('location-track-dialog.track-type')} *`}
                            value={
                                <Dropdown
                                    value={state.locationTrack?.type}
                                    qaId="location-track-type"
                                    options={typeOptions}
                                    onChange={(value) => value && updateProp('type', value)}
                                    onBlur={() => stateActions.onCommitField('type')}
                                    hasError={hasErrors('type')}
                                    wide
                                    searchable
                                />
                            }
                            errors={getVisibleErrorsByProp('type')}
                        />

                        <FieldLayout
                            label={`${t('location-track-dialog.description')}`}
                            value={
                                <div className={styles['location-track-edit-dialog__description']}>
                                    <FieldLayout
                                        label={`${t('location-track-dialog.description-base')} *`}
                                        value={
                                            <TextField
                                                qa-id="location-track-description-base"
                                                value={state.locationTrack?.descriptionBase || ''}
                                                onChange={(e) =>
                                                    updateProp('descriptionBase', e.target.value)
                                                }
                                                onBlur={() =>
                                                    stateActions.onCommitField('descriptionBase')
                                                }
                                                hasError={hasErrors('descriptionBase')}
                                                wide
                                            />
                                        }
                                        errors={getVisibleErrorsByProp('descriptionBase')}
                                    />
                                    <FieldLayout
                                        label={`${t('location-track-dialog.description-suffix')} *`}
                                        value={
                                            <DescriptionSuffixDropdown
                                                qaId="location-track-description-suffix"
                                                suffixMode={locationTrackDescriptionSuffixMode}
                                                onChange={(value) => {
                                                    updateProp('descriptionSuffix', value);
                                                    setLocationTrackDescriptionSuffixMode(value);
                                                }}
                                                onBlur={() =>
                                                    stateActions.onCommitField('descriptionSuffix')
                                                }
                                            />
                                        }
                                        errors={getVisibleErrorsByProp('descriptionSuffix')}
                                    />
                                    <FieldLayout
                                        label={`${t('location-track-dialog.full-description')}`}
                                        value={state.locationTrack && fullDescription()}
                                    />
                                </div>
                            }
                        />

                        <FieldLayout
                            label={`${t('location-track-dialog.duplicate-of')}`}
                            value={
                                <Dropdown
                                    disabled={manuallySetDuplicates.length > 0}
                                    title={
                                        manuallySetDuplicates.length > 0
                                            ? t(
                                                  'location-track-dialog.track-already-has-duplicates',
                                              )
                                            : ''
                                    }
                                    value={
                                        selectedDuplicateTrack && {
                                            type: 'locationTrackSearchItem',
                                            locationTrack: selectedDuplicateTrack,
                                        }
                                    }
                                    getName={(item) => item.locationTrack.name}
                                    placeholder={t('location-track-dialog.search')}
                                    options={getDuplicateTrackOptions}
                                    searchable
                                    onChange={onDuplicateTrackSelected}
                                    onBlur={() => stateActions.onCommitField('duplicateOf')}
                                    canUnselect={true}
                                    unselectText={t('location-track-dialog.not-a-duplicate')}
                                    wideList
                                    wide
                                />
                            }
                        />
                    </FormLayoutColumn>

                    <FormLayoutColumn>
                        <Heading size={HeadingSize.SUB}>
                            {t('location-track-dialog.extra-info-heading')}
                        </Heading>

                        <FieldLayout
                            label={`${t('location-track-dialog.topological-connectivity')} *`}
                            value={
                                <Dropdown
                                    qaId="location-track-topological-connectivity"
                                    value={state.locationTrack?.topologicalConnectivity}
                                    options={topologicalConnectivityOptions}
                                    onChange={(value) =>
                                        value && updateProp('topologicalConnectivity', value)
                                    }
                                    onBlur={() =>
                                        stateActions.onCommitField('topologicalConnectivity')
                                    }
                                    hasError={hasErrors('topologicalConnectivity')}
                                    wide
                                    searchable
                                />
                            }
                            errors={getVisibleErrorsByProp('topologicalConnectivity')}
                        />

                        <FieldLayout
                            label={`${t('location-track-dialog.owner')} * `}
                            value={
                                locationTrackOwners && (
                                    <Dropdown
                                        qaId={'location-track-dialog.owner'}
                                        value={state.locationTrack.ownerId}
                                        options={locationTrackOwners.map((owner) => ({
                                            name: owner.name,
                                            value: owner.id,
                                            qaId: `owner-${owner.id}`,
                                        }))}
                                        onChange={(value) => value && updateProp('ownerId', value)}
                                        onBlur={() => stateActions.onCommitField('ownerId')}
                                        hasError={hasErrors('ownerId')}
                                        wide
                                        searchable
                                    />
                                )
                            }
                            errors={getVisibleErrorsByProp('ownerId')}
                        />

                        <Heading size={HeadingSize.SUB}>
                            {t('location-track-dialog.track-metadata')}
                        </Heading>
                        <FieldLayout
                            label={t('location-track-dialog.start-location')}
                            value={
                                <span
                                    className={
                                        styles['location-track-edit-dialog__readonly-value']
                                    }>
                                    {startAndEndPoints?.start?.address
                                        ? formatTrackMeter(startAndEndPoints.start.address)
                                        : '-'}
                                </span>
                            }
                        />
                        <FieldLayout
                            label={t('location-track-dialog.end-location')}
                            value={
                                <span
                                    className={
                                        styles['location-track-edit-dialog__readonly-value']
                                    }>
                                    {startAndEndPoints?.end?.address
                                        ? formatTrackMeter(startAndEndPoints.end.address)
                                        : '-'}
                                </span>
                            }
                        />
                        <FieldLayout
                            label={t('location-track-dialog.true-length')}
                            value={
                                <span
                                    className={
                                        styles['location-track-edit-dialog__readonly-value']
                                    }>
                                    {state.existingLocationTrack
                                        ? roundToPrecision(
                                              state.existingLocationTrack.length,
                                              Precision.alignmentLengthMeters,
                                          )
                                        : '-'}
                                </span>
                            }
                        />
                        <FieldLayout
                            label={t('location-track-dialog.start-switch')}
                            value={
                                <span
                                    className={
                                        styles['location-track-edit-dialog__readonly-value']
                                    }>
                                    {extraInfo?.switchAtStart?.name ??
                                        t('location-track-dialog.no-start-or-end-switch')}
                                </span>
                            }
                        />
                        <FieldLayout
                            label={t('location-track-dialog.end-switch')}
                            value={
                                <span
                                    className={
                                        styles['location-track-edit-dialog__readonly-value']
                                    }>
                                    {extraInfo?.switchAtEnd?.name ??
                                        t('location-track-dialog.no-start-or-end-switch')}
                                </span>
                            }
                        />
                    </FormLayoutColumn>
                </FormLayout>
            </Dialog>
            {nonDraftDeleteConfirmationVisible && (
                <Dialog
                    title={t('location-track-delete-dialog.title')}
                    variant={DialogVariant.DARK}
                    allowClose={false}
                    footerContent={
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                onClick={() => setNonDraftDeleteConfirmationVisible(false)}
                                variant={ButtonVariant.SECONDARY}
                                disabled={state.isSaving}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                disabled={state.isSaving}
                                isProcessing={state.isSaving}
                                variant={ButtonVariant.PRIMARY_WARNING}
                                onClick={save}>
                                {t('button.delete')}
                            </Button>
                        </div>
                    }>
                    <div className={'dialog__text'}>
                        {t('location-track-delete-dialog.deleted-location-tracks-not-allowed')}
                    </div>
                    <div className={'dialog__text'}>
                        <span className={styles['location-track-edit-dialog__warning']}>
                            <Icons.StatusError color={IconColor.INHERIT} />
                        </span>{' '}
                        {t('location-track-delete-dialog.deleted-location-track-warning')}
                    </div>
                    <div className={'dialog__text'}>
                        {t('location-track-delete-dialog.confirm-location-track-delete')}
                    </div>
                </Dialog>
            )}
            {state.existingLocationTrack && draftDeleteConfirmationVisible && (
                <LocationTrackDeleteConfirmationDialog
                    layoutContext={layoutContextDraft}
                    id={state.existingLocationTrack?.id}
                    onClose={() => setDraftDeleteConfirmationVisible(false)}
                    onSave={() => {
                        props.onSave &&
                            state.existingLocationTrack &&
                            props.onSave(state.existingLocationTrack.id);
                        props.onClose();
                    }}
                />
            )}
        </React.Fragment>
    );
};

type LocationTrackItemValue = {
    locationTrack: LayoutLocationTrack;
    type: 'locationTrackSearchItem';
};

function createDuplicateTrackOptions(
    trackId: LocationTrackId | undefined,
    locationTracks: LayoutLocationTrack[],
    descriptions: LocationTrackDescription[],
): Item<LocationTrackItemValue>[] {
    return locationTracks
        .filter((lt) => lt.id !== trackId && lt.duplicateOf === undefined)
        .map((lt) => {
            const description = descriptions.find((d) => d.id === lt.id)?.description;
            return {
                name: description ? `${lt.name}, ${description}` : lt.name,
                value: {
                    type: 'locationTrackSearchItem',
                    locationTrack: lt,
                },
                qaId: `location-track-${lt.id}`,
            };
        });
}

async function findDuplicateTrackOptions(
    layoutContextDraft: LayoutContext,
    trackId: LocationTrackId | undefined,
    searchTerm: string,
): Promise<Item<LocationTrackItemValue>[]> {
    const locationTracks = await debouncedSearchTracks(searchTerm, layoutContextDraft, 10);
    const trackIds = locationTracks.map((lt) => lt.id);
    const descriptions = await getLocationTrackDescriptions(trackIds, layoutContextDraft);
    return createDuplicateTrackOptions(trackId, locationTracks, descriptions || []);
}
