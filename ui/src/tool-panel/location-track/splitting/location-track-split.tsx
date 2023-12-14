import React from 'react';
import { useTranslation } from 'react-i18next';
import { useLocationTrack } from 'track-layout/track-layout-react-utils';
import { getChangeTimes } from 'common/change-time-api';
import { ValidationError } from 'utils/validation-utils';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import Infobox from 'tool-panel/infobox/infobox';
import { LocationTrackInfoboxVisibilities } from 'track-layout/track-layout-slice';
import TrackMeter from 'geoviite-design-lib/track-meter/track-meter';
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
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import {
    InitialSplit,
    sortSplitsByDistance,
    Split,
    SwitchOnLocationTrack,
} from 'tool-panel/location-track/split-store';
import {
    useLocationTrack,
    useLocationTrackStartAndEnd,
} from 'track-layout/track-layout-react-utils';
import { getChangeTimes } from 'common/change-time-api';
import { BoundingBox } from 'model/geometry';
import {
    calculateBoundingBoxToShowAroundLocation,
    MAP_POINT_CLOSEUP_BBOX_OFFSET,
} from 'map/map-utils';

type LocationTrackInfoboxSplittingProps = {
    duplicateLocationTracks: LocationTrackDuplicate[];
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    initialSplit: InitialSplit;
    splits: Split[];
    allowedSwitches: SwitchOnLocationTrack[];
    removeSplit: (switchId: LayoutSwitchId) => void;
    locationTrackId: string;
    cancelSplitting: () => void;
    updateSplit: (updatedSplit: Split | InitialSplit) => void;
    showArea: (boundingBox: BoundingBox) => void;
};

type EndpointProps = {
    showArea: (boundingBox: BoundingBox) => void;
    addressPoint: AddressPoint | undefined;
};

type SplitProps = EndpointProps & {
    split: Split | InitialSplit;
    onRemove?: (switchId: LayoutSwitchId) => void;
    duplicateLocationTracks?: LocationTrackDuplicate[];
    updateSplit: (updateSplit: Split | InitialSplit) => void;
    duplicateOf: LocationTrackId | undefined;
    nameErrors: ValidationError<Split>[];
    descriptionErrors: ValidationError<Split>[];
};

export const LocationTrackSplittingEndpoint: React.FC<EndpointProps> = ({ address }) => {
    const { t } = useTranslation();
    return (
        <div className={styles['location-track-infobox__split-container']}>
            <div className={styles['location-track-infobox__split-item-ball']} />
            <InfoboxField label={t('tool-panel.location-track.splitting.end-address')}>
                <TrackMeter value={address} />
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
    duplicateLocationTracks = [],
    showArea,
}) => {
    const { t } = useTranslation();
    const switchId = 'switchId' in split ? split.switchId : undefined;
    const [nameCommitted, setNameCommitted] = React.useState(split.name !== '');
    const [descriptionCommitted, setDescriptionCommitted] = React.useState(
        split.descriptionBase !== '',
    );

    const duplicateLocationTrack = useLocationTrack(
        duplicateOf,
        'DRAFT',
        getChangeTimes().layoutLocationTrack,
    );

    const nameErrorsVisible = nameCommitted && nameErrors.length > 0;
    const descriptionErrorsVisible = descriptionCommitted && descriptionErrors.length > 0;

    return (
        <div className={styles['location-track-infobox__split-container']}>
            <div className={styles['location-track-infobox__split-item-line']} />
            <div className={styles['location-track-infobox__split-item-ball']} />
            <div className={styles['location-track-infobox__split-fields-container']}>
                <div>
                    <InfoboxField
                        label={
                            'switchId' in split
                                ? t('tool-panel.location-track.splitting.split-address')
                                : t('tool-panel.location-track.splitting.start-address')
                        }>
                        <TrackMeter
                            onClickAction={() =>
                                addressPoint?.point &&
                                showArea(
                                    calculateBoundingBoxToShowAroundLocation(
                                        addressPoint.point,
                                        MAP_POINT_CLOSEUP_BBOX_OFFSET,
                                    ),
                                )
                            }
                            trackMeter={addressPoint?.address}
                        />
                    </InfoboxField>
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        label={t('tool-panel.location-track.track-name')}
                        hasErrors={nameErrorsVisible}>
                        <TextField
                            value={split.name}
                            hasError={nameErrorsVisible}
                            onChange={(e) => {
                                const duplicateId = duplicateLocationTracks.find(
                                    (lt) => lt.name === e.target.value,
                                )?.id;
                                updateSplit({
                                    ...split,
                                    name: e.target.value,
                                    duplicateOf: duplicateId,
                                });
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
                                            `tool-panel.location-track.splitting.${error.reason}`,
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
                            value={
                                duplicateLocationTrack
                                    ? duplicateLocationTrack.descriptionBase
                                    : split.descriptionBase
                            }
                            hasError={descriptionErrorsVisible}
                            disabled={!!duplicateOf}
                            onChange={(e) => {
                                updateSplit({ ...split, descriptionBase: e.target.value });
                                setDescriptionCommitted(true);
                            }}
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
                                            `tool-panel.location-track.splitting.${error.reason}`,
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
                            disabled={!!duplicateOf}
                        />
                    </InfoboxField>
                </div>
                <div className={styles['location-track-infobox__close-split-button-column']}>
                    {onRemove && (
                        <div
                            className={styles['location-track-infobox__split-close-button']}
                            onClick={() => switchId && onRemove(switchId)}>
                            <Icons.Close size={IconSize.SMALL} color={IconColor.INHERIT} />
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};
