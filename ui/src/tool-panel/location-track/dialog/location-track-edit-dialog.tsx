import React from 'react';
import { LayoutLocationTrack, LocationTrackId } from 'track-layout/track-layout-model';
import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { LocationTrackSaveRequest } from 'linking/linking-model';
import {
    getLocationTrack,
    getLocationTracksBySearchTerm,
    getTrackNumbers,
} from 'track-layout/track-layout-api';
import {
    actions,
    canSaveLocationTrack,
    initialLocationTrackEditState,
    isProcessing,
    reducer,
} from 'tool-panel/location-track/dialog/location-track-edit-store';
import { createDelegates } from 'store/store-utils';
import { Dropdown, Item } from 'vayla-design-lib/dropdown/dropdown';
import {
    layoutStates,
    locationTrackTypes,
    topologicalConnectivityTypes,
} from 'utils/enum-localization-utils';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { insertLocationTrack, updateLocationTrack } from 'linking/linking-api';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { useTranslation } from 'react-i18next';
import {
    useLocationTrack,
    useLocationTrackStartAndEnd,
} from 'track-layout/track-layout-react-utils';
import { formatTrackMeter } from 'utils/geography-utils';
import { Precision, roundToPrecision } from 'utils/rounding';
import { PublishType, TimeStamp } from 'common/common-model';
import LocationTrackDeleteConfirmationDialog from 'tool-panel/location-track/location-track-delete-confirmation-dialog';
import { createClassName } from 'vayla-design-lib/utils';
import { debounceAsync } from 'utils/async-utils';
import { isNullOrBlank } from 'utils/string-utils';
import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';
import styles from './location-track-edit-dialog.scss';

export type LocationTrackDialogProps = {
    locationTrack?: LayoutLocationTrack;
    publishType?: PublishType;
    onClose?: () => void;
    onInsert?: (locationTrackId: LocationTrackId) => void;
    onUpdate?: () => void;
    onUnselect?: () => void;
    locationTrackChangeTime: TimeStamp;
    existingDuplicateTrack?: LayoutLocationTrack | undefined;
    duplicatesExist?: boolean;
};

export const LocationTrackEditDialog: React.FC<LocationTrackDialogProps> = (
    props: LocationTrackDialogProps,
) => {
    const { t } = useTranslation();
    const startAndEndPoints =
        props.locationTrack && props.publishType
            ? useLocationTrackStartAndEnd(props.locationTrack.id, props.publishType)
            : undefined;
    const officialLocationTrack =
        props.publishType && props.locationTrack
            ? useLocationTrack(props.locationTrack.id, 'OFFICIAL', props.locationTrackChangeTime)
            : undefined;
    const firstInputRef = React.useRef<HTMLInputElement>(null);
    const [state, dispatcher] = React.useReducer(reducer, initialLocationTrackEditState);
    const [selectedDuplicateTrack, setSelectedDuplicateTrack] = React.useState<
        LayoutLocationTrack | undefined
    >(props.existingDuplicateTrack);
    const [nonDraftDeleteConfirmationVisible, setNonDraftDeleteConfirmationVisible] =
        React.useState<boolean>(state.locationTrack?.state == 'DELETED');
    const [draftDeleteConfirmationVisible, setDraftDeleteConfirmationVisible] =
        React.useState<boolean>();
    const stateActions = createDelegates(dispatcher, actions);

    const locationTrackStateOptions = layoutStates
        .filter((ls) => !state.isNewLocationTrack || ls.value != 'DELETED')
        .map((ls) => ({ ...ls, disabled: ls.value == 'PLANNED' }));

    // Load track numbers once
    React.useEffect(() => {
        stateActions.onStartLoadingTrackNumbers();
        getTrackNumbers('DRAFT').then((trackNumbers) => {
            stateActions.onTrackNumbersLoaded(trackNumbers);
        });
    }, []);

    // Load an existing location track or create new one
    React.useEffect(() => {
        if (props.locationTrack) {
            stateActions.onStartLoadingLocationTrack();
            getLocationTrack(props.locationTrack.id, 'DRAFT').then((locationTrack) => {
                stateActions.onLocationTrackLoaded(locationTrack);
                firstInputRef.current?.focus();
            });
        } else {
            stateActions.initWithNewLocationTrack();
            firstInputRef.current?.focus();
        }
    }, [props.locationTrack]);

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
        props.locationTrack && props.onUnselect && props.onUnselect();
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
                                Snackbar.success(t('location-track-dialog.created-successfully'));
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
                                        ? t('location-track-dialog.deleted-successfully')
                                        : t('location-track-dialog.modified-successfully');
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

    type SearchItemValue = LocationTrackItemValue;

    function getLocationTrackOptions(searchTerm: string): Promise<Item<SearchItemValue>[]> {
        if (isNullOrBlank(searchTerm)) {
            return Promise.resolve([]);
        }

        return Promise.all([
            props.publishType &&
                getLocationTracksBySearchTerm(
                    searchTerm,
                    props.publishType, // As this is temporary, just use "OFFICIAL" for now
                    10,
                ),
        ]).then((result) => {
            const locationTracks = result[0];
            return props.publishType && locationTracks
                ? locationTracks.map((locationTrack) => ({
                      name: `${locationTrack.name}, ${locationTrack.description}`,
                      value: {
                          type: 'locationTrackSearchItem',
                          locationTrack: locationTrack,
                      },
                  }))
                : [];
        });
    }

    // Use debounced function to collect keystrokes before triggering a search
    const debouncedGetLocationTrackOptions = debounceAsync(getLocationTrackOptions, 250);
    // Use memoized function to make debouncing functionality to work when re-rendering
    const getDuplicateTrackOptions = React.useCallback(
        (searchTerm) =>
            debouncedGetLocationTrackOptions(searchTerm).then((locationTrackItems) =>
                locationTrackItems.filter((locationTrackItem) => {
                    const locationTrack = locationTrackItem.value.locationTrack;
                    return (
                        locationTrack.id !== props.locationTrack?.id &&
                        locationTrack.duplicateOf === null
                    );
                }),
            ),
        [props.locationTrack?.id],
    );

    function onDuplicateTrackSelected(duplicateTrack: SearchItemValue | undefined) {
        updateProp('duplicateOf', duplicateTrack?.locationTrack?.id ?? null);
        setSelectedDuplicateTrack(duplicateTrack?.locationTrack ?? undefined);
    }

    return (
        <div>
            <Dialog
                title={
                    state.isNewLocationTrack
                        ? t('location-track-dialog.title-new')
                        : t('location-track-dialog.title-edit')
                }
                onClose={() => cancelSave()}
                className={dialogStyles['dialog--ultrawide']}
                scrollable={false}
                footerClassName={'dialog-footer'}
                footerContent={
                    <React.Fragment>
                        <div className={dialogStyles['dialog-footer__content-area']}>
                            <div className={dialogStyles['dialog-footer__content--shrink']}>
                                {officialLocationTrack === undefined && !state.isNewLocationTrack && (
                                    <Button
                                        onClick={() =>
                                            props.locationTrack
                                                ? confirmNonDraftDelete()
                                                : undefined
                                        }
                                        icon={Icons.Delete}
                                        variant={ButtonVariant.WARNING}>
                                        {t('location-track-dialog.delete-draft')}
                                    </Button>
                                )}
                            </div>
                            <div
                                className={createClassName(
                                    dialogStyles['dialog-footer__content--grow'],
                                    dialogStyles['dialog-footer__content--centered'],
                                    dialogStyles['dialog-footer__content--padded'],
                                )}>
                                <Button
                                    variant={ButtonVariant.SECONDARY}
                                    disabled={state.isSaving}
                                    onClick={() => cancelSave()}>
                                    {t('button.return')}
                                </Button>
                                <span onClick={() => stateActions.validate()}>
                                    <Button
                                        disabled={!canSaveLocationTrack(state)}
                                        isProcessing={state.isSaving}
                                        onClick={() => saveOrConfirm()}>
                                        {t('button.save')}
                                    </Button>
                                </span>
                            </div>
                        </div>
                    </React.Fragment>
                }>
                <FormLayout isProcessing={isProcessing(state)}>
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
                                    inputRef={firstInputRef}
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
                                    options={state.trackNumbers.map((trackNumber) => ({
                                        name: trackNumber.number,
                                        value: trackNumber.id,
                                    }))}
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
                            label={`${t('location-track-dialog.description')} *`}
                            value={
                                <TextField
                                    value={state.locationTrack?.description || ''}
                                    onChange={(e) => updateProp('description', e.target.value)}
                                    onBlur={() => stateActions.onCommitField('description')}
                                    hasError={hasErrors('description')}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp('description')}
                        />

                        {props.duplicatesExist || props.publishType === undefined || (
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
                    </FormLayoutColumn>

                    <FormLayoutColumn>
                        <Heading size={HeadingSize.SUB}>
                            {t('location-track-dialog.track-metadata')}
                        </Heading>
                        <FieldLayout
                            label={t('location-track-dialog.start-location')}
                            value={
                                <TextField
                                    value={
                                        startAndEndPoints?.start
                                            ? formatTrackMeter(startAndEndPoints.start.address)
                                            : ''
                                    }
                                    wide
                                    disabled
                                />
                            }
                        />
                        <FieldLayout
                            label={t('location-track-dialog.end-location')}
                            value={
                                <TextField
                                    value={
                                        startAndEndPoints?.end
                                            ? formatTrackMeter(startAndEndPoints.end.address)
                                            : ''
                                    }
                                    wide
                                    disabled
                                />
                            }
                        />
                        <FieldLayout
                            label={t('location-track-dialog.true-length')}
                            value={
                                <TextField
                                    value={
                                        props.locationTrack
                                            ? roundToPrecision(
                                                  props.locationTrack.length,
                                                  Precision.alignmentLengthMeters,
                                              )
                                            : ''
                                    }
                                    wide
                                    disabled
                                />
                            }
                        />
                    </FormLayoutColumn>
                </FormLayout>
                {nonDraftDeleteConfirmationVisible && (
                    <Dialog
                        title={t('location-track-delete-dialog.title')}
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
                                <Button variant={ButtonVariant.PRIMARY_WARNING} onClick={save}>
                                    {t('button.delete')}
                                </Button>
                            </React.Fragment>
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
                {props.locationTrack && draftDeleteConfirmationVisible && (
                    <LocationTrackDeleteConfirmationDialog
                        id={props.locationTrack?.id}
                        onCancel={closeDraftDeleteConfirmation}
                        onClose={onLocationTrackDeleted}
                    />
                )}
            </Dialog>
        </div>
    );
};
