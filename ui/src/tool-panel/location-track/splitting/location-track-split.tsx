import React from 'react';
import { useTranslation } from 'react-i18next';
import { useLocationTrack } from 'track-layout/track-layout-react-utils';
import { getChangeTimes } from 'common/change-time-api';
import { ValidationError } from 'utils/validation-utils';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { createClassName } from 'vayla-design-lib/utils';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { DescriptionSuffixDropdown } from 'tool-panel/location-track/description-suffix-dropdown';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import {
    AddressPoint,
    LayoutSwitchId,
    LocationTrackDuplicate,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { InitialSplit, Split } from 'tool-panel/location-track/split-store';
import { MAP_POINT_NEAR_BBOX_OFFSET } from 'map/map-utils';
import NavigableTrackMeter from 'geoviite-design-lib/track-meter/navigable-track-meter';

type EndpointProps = {
    addressPoint: AddressPoint | undefined;
    editingDisabled: boolean;
};

type SplitProps = EndpointProps & {
    split: Split | InitialSplit;
    onRemove?: (switchId: LayoutSwitchId) => void;
    duplicateLocationTracks?: LocationTrackDuplicate[];
    updateSplit: (updateSplit: Split | InitialSplit) => void;
    duplicateOf: LocationTrackId | undefined;
    nameErrors: ValidationError<Split>[];
    descriptionErrors: ValidationError<Split>[];
    switchErrors: ValidationError<Split>[];
    nameRef?: React.RefObject<HTMLInputElement>;
    descriptionBaseRef?: React.RefObject<HTMLInputElement>;
    deletingDisabled: boolean;
};

export const LocationTrackSplittingEndpoint: React.FC<EndpointProps> = ({
    addressPoint,
    editingDisabled,
}) => {
    const { t } = useTranslation();
    return (
        <div className={styles['location-track-infobox__split-container']}>
            <div
                className={createClassName(
                    styles['location-track-infobox__split-item-ball'],
                    editingDisabled && styles['location-track-infobox__split-item-ball--disabled'],
                )}
            />
            <InfoboxField label={t('tool-panel.location-track.splitting.end-address')}>
                <NavigableTrackMeter
                    trackMeter={addressPoint?.address}
                    location={addressPoint?.point}
                    mapNavigationBboxOffset={MAP_POINT_NEAR_BBOX_OFFSET}
                />
            </InfoboxField>
        </div>
    );
};

export const LocationTrackSplit: React.FC<SplitProps> = ({
    split,
    addressPoint,
    onRemove,
    updateSplit,
    duplicateOf,
    nameErrors,
    descriptionErrors,
    switchErrors,
    duplicateLocationTracks = [],
    editingDisabled,
    deletingDisabled,
    nameRef,
    descriptionBaseRef,
}) => {
    const { t } = useTranslation();
    const switchId = 'switchId' in split ? split.switchId : undefined;
    const [nameCommitted, setNameCommitted] = React.useState(split.name !== '');
    const [descriptionCommitted, setDescriptionCommitted] = React.useState(
        split.descriptionBase !== '',
    );

    React.useEffect(() => {
        if (!nameErrors.length) {
            setNameCommitted(true);
        }
        if (!descriptionErrors.length) {
            setDescriptionCommitted(true);
        }
    });

    const duplicateLocationTrack = useLocationTrack(
        duplicateOf,
        'DRAFT',
        getChangeTimes().layoutLocationTrack,
    );

    const nameErrorsVisible = nameCommitted && nameErrors.length > 0;
    const descriptionErrorsVisible = descriptionCommitted && descriptionErrors.length > 0;

    return (
        <div className={styles['location-track-infobox__split-container']}>
            <div
                className={createClassName(
                    styles['location-track-infobox__split-item-line'],
                    editingDisabled && styles['location-track-infobox__split-item-line--disabled'],
                )}
            />
            <div
                className={createClassName(
                    styles['location-track-infobox__split-item-ball'],
                    editingDisabled && styles['location-track-infobox__split-item-ball--disabled'],
                )}
            />
            <div className={styles['location-track-infobox__split-fields-container']}>
                <div>
                    <InfoboxField
                        label={
                            'switchId' in split
                                ? t('tool-panel.location-track.splitting.split-address')
                                : t('tool-panel.location-track.splitting.start-address')
                        }>
                        <div>
                            <NavigableTrackMeter
                                trackMeter={addressPoint?.address}
                                location={addressPoint?.point}
                                mapNavigationBboxOffset={MAP_POINT_NEAR_BBOX_OFFSET}
                            />
                            {switchErrors.some((err) => err.reason === 'switch-not-found') && (
                                <InfoboxText
                                    className={styles['location-track-infobox__split-error']}
                                    value={t(
                                        'tool-panel.location-track.splitting.validation.missing-switch',
                                    )}
                                />
                            )}
                        </div>
                    </InfoboxField>
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        label={t('tool-panel.location-track.track-name')}
                        hasErrors={nameErrorsVisible}>
                        <TextField
                            ref={nameRef}
                            defaultValue={split.name}
                            hasError={nameErrorsVisible}
                            disabled={editingDisabled}
                            onChange={(e) => {
                                const duplicateId = duplicateLocationTracks.find(
                                    (lt) => lt.name === e.target.value,
                                )?.id;
                                updateSplit({
                                    ...split,
                                    name: e.target.value,
                                    duplicateOf: duplicateId,
                                });
                            }}
                            onBlur={() => {
                                setNameCommitted(true);
                            }}
                        />
                    </InfoboxField>
                    {(duplicateOf || nameErrorsVisible) && (
                        <InfoboxField
                            className={createClassName(
                                styles['location-track-infobox__split-remark'],
                            )}
                            hasErrors={nameErrorsVisible}
                            label={''}>
                            {!nameErrorsVisible && duplicateOf && (
                                <InfoboxText
                                    value={t(
                                        'tool-panel.location-track.splitting.replaces-duplicate',
                                    )}
                                />
                            )}
                            {nameErrorsVisible &&
                                nameErrors.map((error, index) => (
                                    <InfoboxText
                                        value={t(
                                            `tool-panel.location-track.splitting.validation.${error.reason}`,
                                        )}
                                        key={index.toString()}
                                    />
                                ))}
                        </InfoboxField>
                    )}
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        hasErrors={descriptionErrorsVisible}
                        label={t('tool-panel.location-track.splitting.description-base')}>
                        <TextField
                            defaultValue={
                                duplicateLocationTrack
                                    ? duplicateLocationTrack.descriptionBase
                                    : split.descriptionBase
                            }
                            hasError={descriptionErrorsVisible}
                            disabled={editingDisabled || !!duplicateOf}
                            onChange={(e) => {
                                updateSplit({ ...split, descriptionBase: e.target.value });
                            }}
                            onBlur={() => {
                                setDescriptionCommitted(true);
                            }}
                            ref={descriptionBaseRef}
                        />
                    </InfoboxField>
                    {descriptionErrorsVisible && (
                        <InfoboxField
                            className={createClassName(
                                styles['location-track-infobox__split-remark'],
                            )}
                            hasErrors={descriptionErrorsVisible}
                            label={''}>
                            {descriptionErrorsVisible &&
                                descriptionErrors.map((error, index) => (
                                    <InfoboxText
                                        value={t(
                                            `tool-panel.location-track.splitting.validation.${error.reason}`,
                                        )}
                                        key={index.toString()}
                                    />
                                ))}
                        </InfoboxField>
                    )}
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        label={t('tool-panel.location-track.splitting.description-suffix')}>
                        <DescriptionSuffixDropdown
                            suffixMode={
                                duplicateLocationTrack
                                    ? duplicateLocationTrack.descriptionSuffix
                                    : split.suffixMode
                            }
                            onChange={(mode) => {
                                updateSplit({ ...split, suffixMode: mode });
                            }}
                            onBlur={() => {}}
                            disabled={editingDisabled || !!duplicateOf}
                        />
                    </InfoboxField>
                </div>
                <div className={styles['location-track-infobox__close-split-button-column']}>
                    {onRemove && (
                        <div
                            className={createClassName(
                                styles['location-track-infobox__split-close-button'],
                                deletingDisabled
                                    ? styles['location-track-infobox__split-close-button--disabled']
                                    : styles['location-track-infobox__split-close-button--enabled'],
                            )}
                            onClick={() => !deletingDisabled && switchId && onRemove(switchId)}>
                            <Icons.Close size={IconSize.SMALL} color={IconColor.INHERIT} />
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};
