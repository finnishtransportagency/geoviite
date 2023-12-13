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
import { TrackMeter as TrackMeterModel } from 'common/common-model';
import {
    LayoutSwitchId,
    LocationTrackDuplicate,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { InitialSplit, Split } from 'tool-panel/location-track/split-store';
import TrackMeter from 'geoviite-design-lib/track-meter/track-meter';

type EndpointProps = {
    address: TrackMeterModel | undefined;
};

type SplitProps = EndpointProps & {
    split: Split | InitialSplit;
    address: TrackMeter | undefined;
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
    address,
    onRemove,
    updateSplit,
    duplicateOf,
    nameErrors,
    descriptionErrors,
    duplicateLocationTracks = [],
}) => {
    const { t } = useTranslation();
    const switchId = 'switchId' in split ? split.switchId : undefined;

    const duplicateLocationTrack = useLocationTrack(
        duplicateOf,
        'DRAFT',
        getChangeTimes().layoutLocationTrack,
    );

    const hasNameRemarks = duplicateOf || nameErrors.length > 0;
    const hasDescriptionRemarks = descriptionErrors.length > 0;

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
                        <TrackMeter value={address} />
                    </InfoboxField>
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        label={t('tool-panel.location-track.track-name')}
                        hasErrors={nameErrors.length > 0}>
                        <TextField
                            value={split.name}
                            hasError={nameErrors.length > 0}
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
                        />
                    </InfoboxField>
                    {hasNameRemarks && (
                        <InfoboxField
                            className={createClassName(
                                styles['location-track-infobox__split-remark'],
                            )}
                            hasErrors={nameErrors.length > 0}
                            label={''}>
                            {duplicateOf && (
                                <InfoboxText
                                    value={t(
                                        'tool-panel.location-track.splitting.replaces-duplicate',
                                    )}
                                />
                            )}
                            {nameErrors.map((error, index) => (
                                <InfoboxText
                                    value={t(`tool-panel.location-track.splitting.${error.reason}`)}
                                    key={index.toString()}
                                />
                            ))}
                        </InfoboxField>
                    )}
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        hasErrors={descriptionErrors.length > 0}
                        label={t('tool-panel.location-track.splitting.description-base')}>
                        <TextField
                            value={
                                duplicateLocationTrack
                                    ? duplicateLocationTrack.descriptionBase
                                    : split.descriptionBase
                            }
                            hasError={descriptionErrors.length > 0}
                            disabled={!!duplicateOf}
                            onChange={(e) =>
                                updateSplit({ ...split, descriptionBase: e.target.value })
                            }
                        />
                    </InfoboxField>
                    {hasDescriptionRemarks && (
                        <InfoboxField
                            className={createClassName(
                                styles['location-track-infobox__split-remark'],
                            )}
                            hasErrors={descriptionErrors.length > 0}
                            label={''}>
                            {descriptionErrors.map((error, index) => (
                                <InfoboxText
                                    value={t(`tool-panel.location-track.splitting.${error.reason}`)}
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
