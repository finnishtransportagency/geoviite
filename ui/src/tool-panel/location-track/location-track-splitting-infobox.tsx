import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import Infobox from 'tool-panel/infobox/infobox';
import { LocationTrackInfoboxVisibilities } from 'track-layout/track-layout-slice';
import TrackMeter from 'geoviite-design-lib/track-meter/track-meter';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { DescriptionSuffixDropdown } from 'tool-panel/location-track/description-suffix-dropdown';
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
    MAP_POINT_CLOSEUP_BOUNDING_BOX_OFFSET,
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
};

const Split: React.FC<SplitProps> = ({
    split,
    addressPoint,
    onRemove,
    updateSplit,
    duplicateOf,
    duplicateLocationTracks = [],
    showArea,
}) => {
    const { t } = useTranslation();
    const switchId = 'switchId' in split ? split.switchId : undefined;

    const duplicateLocationTrack = useLocationTrack(
        duplicateOf,
        'DRAFT',
        getChangeTimes().layoutLocationTrack,
    );

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
                            onShowOnMap={() =>
                                addressPoint?.point &&
                                showArea(
                                    calculateBoundingBoxToShowAroundLocation(
                                        addressPoint.point,
                                        MAP_POINT_CLOSEUP_BOUNDING_BOX_OFFSET,
                                    ),
                                )
                            }
                            addressPoint={addressPoint}
                        />
                    </InfoboxField>
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        label={t('tool-panel.location-track.track-name')}>
                        <TextField
                            value={split.name}
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
                    {duplicateOf && (
                        <InfoboxField
                            className={styles['location-track-infobox__split-replaces-duplicate']}
                            label={''}>
                            <InfoboxText
                                value={t('tool-panel.location-track.splitting.replaces-duplicate')}
                            />
                        </InfoboxField>
                    )}
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        label={t('tool-panel.location-track.splitting.description-base')}>
                        <TextField
                            value={
                                duplicateLocationTrack
                                    ? duplicateLocationTrack.descriptionBase
                                    : split.descriptionBase
                            }
                            disabled={!!duplicateOf}
                            onChange={(e) =>
                                updateSplit({ ...split, descriptionBase: e.target.value })
                            }
                        />
                    </InfoboxField>
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

const Endpoint: React.FC<EndpointProps> = ({ showArea, addressPoint }) => {
    const { t } = useTranslation();
    return (
        <div className={styles['location-track-infobox__split-container']}>
            <div className={styles['location-track-infobox__split-item-ball']} />
            <InfoboxField label={t('tool-panel.location-track.splitting.end-address')}>
                <TrackMeter
                    onShowOnMap={() =>
                        addressPoint?.point &&
                        showArea(
                            calculateBoundingBoxToShowAroundLocation(
                                addressPoint.point,
                                MAP_POINT_CLOSEUP_BOUNDING_BOX_OFFSET,
                            ),
                        )
                    }
                    addressPoint={addressPoint}
                />
            </InfoboxField>
        </div>
    );
};

export const LocationTrackSplittingInfobox: React.FC<LocationTrackInfoboxSplittingProps> = ({
    duplicateLocationTracks,
    visibilities,
    visibilityChange,
    locationTrackId,
    initialSplit,
    splits,
    allowedSwitches,
    removeSplit,
    cancelSplitting,
    updateSplit,
    showArea,
}) => {
    const { t } = useTranslation();
    const [startAndEnd, _] = useLocationTrackStartAndEnd(locationTrackId, 'DRAFT');

    const getSplitAddressPoint = (split: Split): AddressPoint | undefined => {
        const switchAtSplit = allowedSwitches.find((s) => s.switchId === split.switchId);

        if (switchAtSplit?.location && switchAtSplit?.address) {
            return {
                point: { ...switchAtSplit.location, m: -1 },
                address: switchAtSplit.address,
            };
        }

        return undefined;
    };

    const sortedSplits = sortSplitsByDistance(splits);

    return (
        <React.Fragment>
            {startAndEnd?.start && startAndEnd?.end && (
                <Infobox
                    contentVisible={visibilities.splitting}
                    onContentVisibilityChange={() => visibilityChange('splitting')}
                    title={t('tool-panel.location-track.splitting.title')}>
                    <InfoboxContent className={styles['location-track-infobox__split']}>
                        <Split
                            split={initialSplit}
                            addressPoint={startAndEnd?.start}
                            duplicateLocationTracks={duplicateLocationTracks}
                            updateSplit={updateSplit}
                            duplicateOf={initialSplit.duplicateOf}
                            showArea={showArea}
                        />
                        {sortedSplits.map((split, index) => {
                            return (
                                <Split
                                    key={index.toString()}
                                    split={split}
                                    addressPoint={getSplitAddressPoint(split)}
                                    onRemove={removeSplit}
                                    duplicateLocationTracks={duplicateLocationTracks}
                                    updateSplit={updateSplit}
                                    duplicateOf={split.duplicateOf}
                                    showArea={showArea}
                                />
                            );
                        })}
                        <Endpoint showArea={showArea} addressPoint={startAndEnd.end} />
                        {splits.length === 0 && (
                            <InfoboxContentSpread>
                                <MessageBox>
                                    {t('tool-panel.location-track.splitting.splitting-guide')}
                                </MessageBox>
                            </InfoboxContentSpread>
                        )}
                        <InfoboxButtons>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                size={ButtonSize.SMALL}
                                onClick={cancelSplitting}>
                                {t('button.cancel')}
                            </Button>
                            <Button size={ButtonSize.SMALL}>
                                {t('tool-panel.location-track.splitting.confirm-split')}
                            </Button>
                        </InfoboxButtons>
                    </InfoboxContent>
                </Infobox>
            )}
        </React.Fragment>
    );
};
