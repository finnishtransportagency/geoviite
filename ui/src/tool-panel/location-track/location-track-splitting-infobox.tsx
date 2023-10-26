import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import Infobox from 'tool-panel/infobox/infobox';
import { LocationTrackInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { TrackMeter as TrackMeterModel } from 'common/common-model';
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
import {
    InitialSplit,
    Split,
    SplitDuplicate,
    SwitchOnLocationTrack,
} from 'tool-panel/location-track/split-store';
import {
    useLocationTrack,
    useLocationTrackStartAndEnd,
} from 'track-layout/track-layout-react-utils';
import { getChangeTimes } from 'common/change-time-api';

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
    setSplitDuplicate: (splitDuplicate: SplitDuplicate | undefined) => void;
};

type EndpointProps = {
    location: TrackMeterModel | undefined;
};

type SplitProps = EndpointProps & {
    isInitial: boolean;
    switchId?: LayoutSwitchId;
    onRemove?: (switchId: LayoutSwitchId) => void;
    duplicateLocationTracks?: LocationTrackDuplicate[];
    setSplitDuplicate: (splitDuplicate: SplitDuplicate | undefined) => void;
    duplicateOf: LocationTrackId | undefined;
};

const Split: React.FC<SplitProps> = ({
    location,
    switchId,
    isInitial,
    onRemove,
    setSplitDuplicate,
    duplicateOf,
    duplicateLocationTracks = [],
}) => {
    const [name, setName] = React.useState<string>('');
    const [descriptionBase, setDescriptionBase] = React.useState<string>('');
    const [suffixMode, setSuffixMode] = React.useState<LocationTrackDescriptionSuffixMode>('NONE');
    React.useEffect(() => {
        const duplicateId = duplicateLocationTracks.find((lt) => lt.name === name)?.id;
        setSplitDuplicate({ splitId: switchId, duplicateOf: duplicateId });
    }, [name, duplicateLocationTracks]);
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
                        label={isInitial ? 'Alkusijainti (km+m)' : 'Katkaisukohta (km+m)'}>
                        <TrackMeter value={location} />
                    </InfoboxField>
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        label={'Sijaintiraidetunnus'}>
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
                            <InfoboxText value={'Korvaa duplikaattiraiteen'} />
                        </InfoboxField>
                    )}
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        label={'Kuvauksen perusosa'}>
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
                        label={'Kuvauksen lisäosa'}>
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
    return (
        <div className={styles['location-track-infobox__split-container']}>
            <div className={styles['location-track-infobox__split-item-ball']} />
            <InfoboxField label={'Loppusijainti (km+m)'}>
                <TrackMeter value={location} />
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
    setSplitDuplicate,
}) => {
    const { t } = useTranslation();
    const [startAndEnd, _] = useLocationTrackStartAndEnd(locationTrackId, 'DRAFT');

    const getSplitLocation = (split: Split) =>
        allowedSwitches.find((s) => s.switchId === split.switchId)?.address;

    return (
        <Infobox
            contentVisible={visibilities.splitting}
            onContentVisibilityChange={() => visibilityChange('splitting')}
            title={'Raiteen jakaminen osiin'}>
            <InfoboxContent className={styles['location-track-infobox__split']}>
                <Split
                    location={startAndEnd?.start?.address}
                    isInitial={true}
                    duplicateLocationTracks={duplicateLocationTracks}
                    setSplitDuplicate={setSplitDuplicate}
                    duplicateOf={initialSplit.duplicateOf}
                />
                {splits.map((split, index) => {
                    return (
                        <Split
                            key={index.toString()}
                            location={getSplitLocation(split)}
                            isInitial={false}
                            switchId={split.switchId}
                            onRemove={removeSplit}
                            duplicateLocationTracks={duplicateLocationTracks}
                            setSplitDuplicate={setSplitDuplicate}
                            duplicateOf={split.duplicateOf}
                        />
                    );
                })}
                <Endpoint location={startAndEnd?.end?.address} />
                {splits.length === 0 && (
                    <InfoboxContentSpread>
                        <MessageBox>
                            {t('Valitse kartalta vaihteet, joiden kohdalta raide katkaistaan')}
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
                    <Button size={ButtonSize.SMALL}>{t('Suorita jako')}</Button>
                </InfoboxButtons>
            </InfoboxContent>
        </Infobox>
    );
};
