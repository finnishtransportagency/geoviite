import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import Infobox from 'tool-panel/infobox/infobox';
import { LocationTrackInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { compareTrackMeterStrings, TrackMeter as TrackMeterModel } from 'common/common-model';
import TrackMeter from 'geoviite-design-lib/track-meter/track-meter';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { DescriptionSuffixDropdown } from 'tool-panel/location-track/description-suffix-dropdown';
import {
    LayoutSwitchId,
    LocationTrackDescriptionSuffixMode,
    LocationTrackDuplicate,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import { InitialSplit, Split, SwitchOnLocationTrack } from 'tool-panel/location-track/split-store';
import {
    useLocationTrack,
    useLocationTrackStartAndEnd,
} from 'track-layout/track-layout-react-utils';
import { getChangeTimes } from 'common/change-time-api';
import { formatTrackMeter } from 'utils/geography-utils';

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
};

type EndpointProps = {
    location: TrackMeterModel | undefined;
};

type SplitProps = EndpointProps & {
    isInitial: boolean;
    switchId?: LayoutSwitchId;
    onRemove?: (switchId: LayoutSwitchId) => void;
    duplicateLocationTracks?: LocationTrackDuplicate[];
    updateSplit: (updateSplit: Split | InitialSplit) => void;
    duplicateOf: LocationTrackId | undefined;
};

const Split: React.FC<SplitProps> = ({
    location,
    switchId,
    isInitial,
    onRemove,
    updateSplit,
    duplicateOf,
    duplicateLocationTracks = [],
}) => {
    const { t } = useTranslation();
    const [name, setName] = React.useState<string>('');
    const [descriptionBase, setDescriptionBase] = React.useState<string>('');
    const [suffixMode, setSuffixMode] = React.useState<LocationTrackDescriptionSuffixMode>('NONE');

    React.useEffect(() => {
        const duplicateId = duplicateLocationTracks.find((lt) => lt.name === name)?.id;
        const updatedSwitchBase = {
            name,
            descriptionBase,
            suffixMode,
            duplicateOf: duplicateId,
        };
        const finalUpdatedSwitch = switchId
            ? { ...updatedSwitchBase, switchId }
            : updatedSwitchBase;
        updateSplit(finalUpdatedSwitch);
    }, [name, descriptionBase, suffixMode, duplicateLocationTracks]);

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
                            isInitial
                                ? t('tool-panel.location-track.splitting.start-address')
                                : t('tool-panel.location-track.splitting.split-address')
                        }>
                        <TrackMeter value={location} />
                    </InfoboxField>
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        label={t('tool-panel.location-track.track-name')}>
                        <TextField
                            value={name}
                            onChange={(e) => {
                                const newName = e.target.value;
                                setName(newName);
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
                                    : descriptionBase
                            }
                            disabled={!!duplicateOf}
                            onChange={(e) => setDescriptionBase(e.target.value)}
                        />
                    </InfoboxField>
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        label={t('tool-panel.location-track.splitting.description-suffix')}>
                        <DescriptionSuffixDropdown
                            suffixMode={
                                duplicateLocationTrack
                                    ? duplicateLocationTrack.descriptionSuffix
                                    : suffixMode
                            }
                            onChange={(mode) => {
                                setSuffixMode(mode);
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

const Endpoint: React.FC<EndpointProps> = ({ location }) => {
    const { t } = useTranslation();
    return (
        <div className={styles['location-track-infobox__split-container']}>
            <div className={styles['location-track-infobox__split-item-ball']} />
            <InfoboxField label={t('tool-panel.location-track.splitting.end-address')}>
                <TrackMeter value={location} />
            </InfoboxField>
        </div>
    );
};

const sortSplitsBySwitchLocation = (splits: Split[], allowedSwitches: SwitchOnLocationTrack[]) =>
    [...splits].sort((a, b) => {
        const aAddress = allowedSwitches.find((sw) => sw.switchId === a.switchId)?.address;
        const bAddress = allowedSwitches.find((sw) => sw.switchId === b.switchId)?.address;
        if (aAddress && bAddress) {
            return compareTrackMeterStrings(formatTrackMeter(aAddress), formatTrackMeter(bAddress));
        } else if (aAddress) return 1;
        else if (bAddress) return -1;
        else return 0;
    });

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
}) => {
    const { t } = useTranslation();
    const [startAndEnd, _] = useLocationTrackStartAndEnd(locationTrackId, 'DRAFT');

    const getSplitLocation = (split: Split) =>
        allowedSwitches.find((s) => s.switchId === split.switchId)?.address;

    const sortedSplits = sortSplitsBySwitchLocation(splits, allowedSwitches);

    return (
        <Infobox
            contentVisible={visibilities.splitting}
            onContentVisibilityChange={() => visibilityChange('splitting')}
            title={t('tool-panel.location-track.splitting.title')}>
            <InfoboxContent className={styles['location-track-infobox__split']}>
                <Split
                    location={startAndEnd?.start?.address}
                    isInitial={true}
                    duplicateLocationTracks={duplicateLocationTracks}
                    updateSplit={updateSplit}
                    duplicateOf={initialSplit.duplicateOf}
                />
                {sortedSplits.map((split, index) => {
                    return (
                        <Split
                            key={index.toString()}
                            location={getSplitLocation(split)}
                            isInitial={false}
                            switchId={split.switchId}
                            onRemove={removeSplit}
                            duplicateLocationTracks={duplicateLocationTracks}
                            updateSplit={updateSplit}
                            duplicateOf={split.duplicateOf}
                        />
                    );
                })}
                <Endpoint location={startAndEnd?.end?.address} />
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
    );
};
