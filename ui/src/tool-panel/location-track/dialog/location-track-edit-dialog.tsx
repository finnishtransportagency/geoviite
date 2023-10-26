import React from 'react';
import {
    LayoutLocationTrack,
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
} from 'tool-panel/location-track/dialog/location-track-edit-store';
import { createDelegatesWithDispatcher } from 'store/store-utils';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import {
    descriptionSuffixModes,
    layoutStates,
    locationTrackTypes,
    topologicalConnectivityTypes,
} from 'utils/enum-localization-utils';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { useTranslation } from 'react-i18next';
import {
    useLocationTrackInfoboxExtras,
    useLocationTrackStartAndEnd,
} from 'track-layout/track-layout-react-utils';
import { formatTrackMeter } from 'utils/geography-utils';
import { Precision, roundToPrecision } from 'utils/rounding';
import { PublishType, TimeStamp } from 'common/common-model';
import LocationTrackDeleteConfirmationDialog from 'tool-panel/location-track/location-track-delete-confirmation-dialog';
import { debounceAsync } from 'utils/async-utils';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import styles from './location-track-edit-dialog.scss';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

export type LocationTrackDialogProps = {
    locationTrack?: LayoutLocationTrack;
    publishType: PublishType;
    onClose?: () => void;
    onInsert?: (locationTrackId: LocationTrackId) => void;
    onUpdate?: () => void;
    onUnselect?: () => void;
    locationTrackChangeTime: TimeStamp;
    existingDuplicateTrack?: LayoutLocationTrack | undefined;
    duplicatesExist?: boolean;
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
    >(props.existingDuplicateTrack);
    const [nonDraftDeleteConfirmationVisible, setNonDraftDeleteConfirmationVisible] =
        React.useState<boolean>(state.locationTrack?.state == 'DELETED');
    const [draftDeleteConfirmationVisible, setDraftDeleteConfirmationVisible] =
        React.useState<boolean>();
    const [locationTrackDescriptionSuffixMode, setLocationTrackDescriptionSuffixMode] =
        React.useState<LocationTrackDescriptionSuffixMode>();
    const stateActions = createDelegatesWithDispatcher(dispatcher, actions);

    React.useEffect(() => {
        setLocationTrackDescriptionSuffixMode(state.locationTrack?.descriptionSuffix);
    }, [state.locationTrack]);
    const [startAndEndPoints, _] = useLocationTrackStartAndEnd(
        state.existingLocationTrack?.id,
        props.publishType,
        props.locationTrackChangeTime,
    );

    const [extraInfo] = useLocationTrackInfoboxExtras(props.locationTrack?.id, props.publishType);

    const locationTrackStateOptions = layoutStates
        .filter((ls) => !state.isNewLocationTrack || ls.value != 'DELETED')
        .map((ls) => ({ ...ls, disabled: ls.value == 'PLANNED' }));

    const closeNonDraftDeleteConfirmation = () => {
        setNonDraftDeleteConfirmationVisible(false);
    };

    const confirmNonDraftDelete = () => {
        setDraftDeleteConfirmationVisible(true);
    };

    const closeDraftDeleteConfirmation = () => {
        setDraftDeleteConfirmationVisible(false);
    };

    const onLocationTrackDeleted = () => {
        closeNonDraftDeleteConfirmation();
        props.onClose && props.onClose();
        state.existingLocationTrack && props.onUnselect && props.onUnselect();
    };

    // Load track numbers once
    React.useEffect(() => {
        stateActions.onStartLoadingTrackNumbers();
        getTrackNumbers('DRAFT', undefined, true).then((trackNumbers) => {
            stateActions.onTrackNumbersLoaded(trackNumbers);
        });
    }, []);

    // Load an existing location track or create new one
    React.useEffect(() => {
        if (props.locationTrack) {
            stateActions.onStartLoadingLocationTrack();
            getLocationTrack(props.locationTrack.id, 'DRAFT').then((locationTrack) => {
                if (locationTrack) {
                    stateActions.onLocationTrackLoaded(locationTrack);
                    firstInputRef.current?.focus();
                } else {
                    Snackbar.error('location-track-dialog.cant-open-deleted');
                    onLocationTrackDeleted();
                }
            });
        } else {
            stateActions.initWithNewLocationTrack();
            firstInputRef.current?.focus();
        }
    }, [props.locationTrack?.id]);

    function cancelSave() {
        props.onClose && props.onClose();
    }

    const saveOrConfirm = () => {
        if (state.locationTrack?.state === 'DELETED') {
            setNonDraftDeleteConfirmationVisible(true);
        } else {
            save();
        }
    };

    function save() {
        if (canSaveLocationTrack(state) && state.locationTrack) {
            stateActions.onStartSaving();
            if (state.isNewLocationTrack) {
                insertLocationTrack(state.locationTrack)
                    .then((result) => {
                        result
                            .map((locationTrackId) => {
                                stateActions.onSaveSucceed(locationTrackId);
                                props.onInsert && props.onInsert(locationTrackId);
                                Snackbar.success('location-track-dialog.created-successfully');
                                props.onClose && props.onClose();
                            })
                            .mapErr((_err) => {
                                stateActions.onSaveFailed();
                            });
                    })
                    .catch(() => {
                        stateActions.onSaveFailed();
                    });
            } else if (state.existingLocationTrack) {
                updateLocationTrack(state.existingLocationTrack.id, state.locationTrack)
                    .then((result) => {
                        result
                            .map((locationTrackId) => {
                                stateActions.onSaveSucceed(locationTrackId);
                                props.onUpdate && props.onUpdate();
                                const successMessage =
                                    state.locationTrack?.state === 'DELETED'
                                        ? 'location-track-dialog.deleted-successfully'
                                        : 'location-track-dialog.modified-successfully';
                                Snackbar.success(successMessage);
                                props.onClose && props.onClose();
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
            ? state.validationErrors
                  .filter((error) => error.field == prop)
                  .map((error) => t(`location-track-dialog.${error.reason}`))
            : [];
    }

    function hasErrors(prop: keyof LocationTrackSaveRequest) {
        return getVisibleErrorsByProp(prop).length > 0;
    }

    type LocationTrackItemValue = {
        locationTrack: LayoutLocationTrack;
        type: 'locationTrackSearchItem';
    };

    // Use memoized function to make debouncing functionality to work when re-rendering
    const getDuplicateTrackOptions = React.useCallback(
        (searchTerm: string) =>
            debouncedSearchTracks(searchTerm, props.publishType, 10).then((locationTracks) =>
                getLocationTrackDescriptions(
                    locationTracks.map((lt) => lt.id),
                    props.publishType,
                ).then((descriptions) => {
                    return locationTracks
                        .filter((lt) => {
                            return (
                                lt.id !== props.locationTrack?.id && lt.duplicateOf === undefined
                            );
                        })
                        .map((lt) => {
                            const description = descriptions?.find((d) => d.id === lt.id)
                                ?.description;
                            return {
                                name: description ? `${lt.name}, ${description}` : lt.name,
                                value: {
                                    type: 'locationTrackSearchItem',
                                    locationTrack: lt,
                                },
                            };
                        });
                }),
            ),
        [props.locationTrack?.id],
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

    const descriptionSuffix = (mode: LocationTrackDescriptionSuffixMode) => {
        switch (mode) {
            case 'NONE':
                return undefined;
            case 'SWITCH_TO_SWITCH':
                return switchToSwitchDescriptionSuffix();
            case 'SWITCH_TO_BUFFER':
                return switchToBufferDescriptionSuffix();
            default:
                exhaustiveMatchingGuard(mode);
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
        const numberPart = last[0] === 'V' ? last.substring(1) : '';
        const number = parseInt(numberPart, 10);
        return !isNaN(number) ? `V${number.toString(10).padStart(3, '0')}` : undefined;
    };
    const trackNumberOptions = state.trackNumbers
        .filter(
            (tn) => tn.id === state.existingLocationTrack?.trackNumberId || tn.state !== 'DELETED',
        )
        .map((tn) => {
            const note = tn.state === 'DELETED' ? ` (${t('enum.layout-state.DELETED')})` : '';
            return { name: tn.number + note, value: tn.id };
        });

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
                        {state.existingLocationTrack?.draftType === 'NEW_DRAFT' &&
                            !state.isNewLocationTrack && (
                                <div
                                    className={
                                        dialogStyles['dialog__footer-content--left-aligned']
                                    }>
                                    <Button
                                        onClick={() =>
                                            state.existingLocationTrack && confirmNonDraftDelete()
                                        }
                                        icon={Icons.Delete}
                                        variant={ButtonVariant.WARNING}>
                                        {t('location-track-dialog.delete-draft')}
                                    </Button>
                                </div>
                            )}
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                disabled={state.isSaving}
                                onClick={() => cancelSave()}>
                                {t('button.return')}
                            </Button>
                            <Button
                                disabled={!canSaveLocationTrack(state)}
                                isProcessing={state.isSaving}
                                onClick={() => {
                                    stateActions.validate();
                                    saveOrConfirm();
                                }}>
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
                                    value={state.locationTrack?.name}
                                    onChange={(e) => updateProp('name', e.target.value)}
                                    onBlur={() => stateActions.onCommitField('name')}
                                    hasError={hasErrors('name')}
                                    ref={firstInputRef}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp('name')}
                        />
                        <FieldLayout
                            label={`${t('location-track-dialog.track-number')} *`}
                            value={
                                <Dropdown
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
                                    value={state.locationTrack?.state}
                                    options={locationTrackStateOptions}
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
                                    options={locationTrackTypes}
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
                                            <Dropdown
                                                options={descriptionSuffixModes}
                                                value={locationTrackDescriptionSuffixMode}
                                                onChange={(value) => {
                                                    updateProp('descriptionSuffix', value);
                                                    setLocationTrackDescriptionSuffixMode(value);
                                                }}
                                                onBlur={() =>
                                                    stateActions.onCommitField('descriptionSuffix')
                                                }
                                                canUnselect={false}
                                                wideList
                                                wide
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

                        {props.duplicatesExist || (
                            <FieldLayout
                                label={`${t('location-track-dialog.duplicate-of')}`}
                                value={
                                    <Dropdown
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
                        )}
                    </FormLayoutColumn>

                    <FormLayoutColumn>
                        <Heading size={HeadingSize.SUB}>
                            {t('location-track-dialog.extra-info-heading')}
                        </Heading>

                        <FieldLayout
                            label={`${t('location-track-dialog.topological-connectivity')} *`}
                            value={
                                <Dropdown
                                    value={state.locationTrack?.topologicalConnectivity}
                                    options={topologicalConnectivityTypes}
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
                            label={t('location-track-dialog.owner')}
                            value={
                                <Dropdown
                                    value={undefined}
                                    options={[]}
                                    onChange={(_value) => undefined}
                                    onBlur={() => undefined}
                                    wide
                                    disabled
                                    searchable
                                />
                            }
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
                                    {startAndEndPoints?.start
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
                                    {startAndEndPoints?.end
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
                                onClick={closeNonDraftDeleteConfirmation}
                                variant={ButtonVariant.SECONDARY}>
                                {t('button.cancel')}
                            </Button>
                            <Button variant={ButtonVariant.PRIMARY_WARNING} onClick={save}>
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
                    id={state.existingLocationTrack?.id}
                    onCancel={closeDraftDeleteConfirmation}
                    onClose={onLocationTrackDeleted}
                />
            )}
        </React.Fragment>
    );
};
