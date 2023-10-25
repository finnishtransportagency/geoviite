import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import Infobox from 'tool-panel/infobox/infobox';
import { LocationTrackInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { SwitchStructure, TrackMeter as TrackMeterModel } from 'common/common-model';
import TrackMeter from 'geoviite-design-lib/track-meter/track-meter';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { DescriptionSuffixDropdown } from 'tool-panel/location-track/description-suffix-dropdown';
import {
    AlignmentStartAndEnd,
    LayoutSwitch,
    LayoutSwitchId,
    LocationTrackDescriptionSuffixMode,
    LocationTrackId,
    SwitchJointTrackMeter,
} from 'track-layout/track-layout-model';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Point } from 'model/geometry';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import { LocationTrackBoundaryEndpoint, Split } from 'tool-panel/location-track/split-store';
import { useLocationTrackStartAndEnd } from 'track-layout/track-layout-react-utils';
import { getSwitches, getSwitchJointConnections } from 'track-layout/layout-switch-api';
import { useLoader } from 'utils/react-utils';
import { getSwitchStructures } from 'common/common-api';
import { getSwitchJointTrackMeters } from 'tool-panel/switch/switch-infobox';
import { getChangeTimes } from 'common/change-time-api';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type LocationTrackInfoboxSplittingProps = {
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    splits: Split[];
    endPoint: LocationTrackBoundaryEndpoint;
    removeSplit: (startTrackMeter: TrackMeterModel) => void;
    addSplit: (split: { point: Point; address: TrackMeterModel }) => void;
    locationTrackId: string;
    cancelSplitting: () => void;
};

type EndpointProps = {
    location: TrackMeterModel | undefined;
};

type SplitProps = EndpointProps & {
    isInitial: boolean;
    onRemove?: (startTrackMeter: TrackMeterModel) => void;
};

const Split: React.FC<SplitProps> = ({ location, isInitial, onRemove: _onRemove }) => {
    const [name, setName] = React.useState<string>('');
    const [descriptionBase, setDescriptionBase] = React.useState<string>('');
    const [suffixMode, setSuffixMode] = React.useState<LocationTrackDescriptionSuffixMode>('NONE');
    const [replacesDuplicate, setReplacesDuplicate] = React.useState<boolean>(false);

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
                    <InfoboxField label={'Sijaintiraidetunnus'}>
                        <TextField
                            value={name}
                            onChange={(e) => {
                                const newName = e.target.value;
                                setName(newName);
                                setReplacesDuplicate(newName === 'HAS 002');
                            }}
                        />
                    </InfoboxField>
                    {replacesDuplicate && (
                        <InfoboxField
                            className={styles['location-track-infobox__split-replaces-duplicate']}
                            label={''}>
                            <InfoboxText value={'Korvaa duplikaattiraiteen'} />
                        </InfoboxField>
                    )}
                    <InfoboxField label={'Kuvauksen perusosa'}>
                        <TextField
                            value={descriptionBase}
                            disabled={replacesDuplicate}
                            onChange={(e) => setDescriptionBase(e.target.value)}
                        />
                    </InfoboxField>
                    <InfoboxField label={'Kuvauksen lisäosa'}>
                        <DescriptionSuffixDropdown
                            suffixMode={suffixMode}
                            onChange={(mode) => {
                                setSuffixMode(mode);
                            }}
                            onBlur={() => {}}
                            disabled={replacesDuplicate}
                        />
                    </InfoboxField>
                </div>
                <div className={styles['location-track-infobox__close-split-button-column']}>
                    {!isInitial && (
                        <div
                            className={styles['location-track-infobox__split-close-button']}
                            //onClick={() => onRemove && onRemove(location)}
                        >
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

const switchLocation = (
    switchesAndTheirJoints: { switch: LayoutSwitch; meters: SwitchJointTrackMeter[] }[],
    switchStructures: SwitchStructure[],
    switchId: LayoutSwitchId,
    locationTrackId: LocationTrackId,
) => {
    const switchAndJoints = switchesAndTheirJoints.find((s) => s.switch.id === switchId);
    const structure =
        switchAndJoints &&
        switchStructures.find((s) => s.id === switchAndJoints.switch.switchStructureId);
    return (
        switchAndJoints &&
        structure &&
        switchAndJoints.meters.find(
            (m) =>
                m.jointNumber === structure.presentationJointNumber &&
                m.locationTrackId === locationTrackId,
        )?.trackMeter
    );
};

const locationTrackEndpointAddress = (
    endpoint: LocationTrackBoundaryEndpoint,
    startAndEnd: AlignmentStartAndEnd,
) => {
    switch (endpoint.type) {
        case 'LOCATION_TRACK_START':
            return startAndEnd.start?.address;
        case 'LOCATION_TRACK_END':
            return startAndEnd.end?.address;
        default:
            exhaustiveMatchingGuard(endpoint.type);
    }
};

export const LocationTrackSplittingInfobox: React.FC<LocationTrackInfoboxSplittingProps> = ({
    visibilities,
    visibilityChange,
    locationTrackId,
    splits,
    endPoint,
    removeSplit,
    addSplit: _addSplit,
    cancelSplitting,
}) => {
    const { t } = useTranslation();
    const [startAndEnd, _] = useLocationTrackStartAndEnd(locationTrackId, 'DRAFT');
    const switchStructures = useLoader(() => getSwitchStructures(), []);
    const switchIds = splits
        .map((split) => (split.start.type === 'SWITCH' ? split.start.switchId : undefined))
        .filter((id): id is string => id !== undefined);
    const switchesAndJointTrackMeters = useLoader(
        () =>
            getSwitches(switchIds, 'DRAFT').then((switches) =>
                Promise.all(
                    switches.map((s) =>
                        getSwitchJointConnections('DRAFT', s.id).then((connections) =>
                            getSwitchJointTrackMeters(connections, 'DRAFT', getChangeTimes()).then(
                                (jointTrackMeters) => ({ switch: s, meters: jointTrackMeters }),
                            ),
                        ),
                    ),
                ),
            ),
        [switchIds],
    );

    const getSplitLocation = (split: Split) => {
        return split.start.type === 'SWITCH'
            ? switchesAndJointTrackMeters &&
                  switchStructures &&
                  switchLocation(
                      switchesAndJointTrackMeters,
                      switchStructures,
                      split.start.switchId,
                      locationTrackId,
                  )
            : startAndEnd && locationTrackEndpointAddress(split.start, startAndEnd);
    };

    return (
        <Infobox
            contentVisible={visibilities.splitting}
            onContentVisibilityChange={() => visibilityChange('splitting')}
            title={'Raiteen jakaminen osiin'}>
            <InfoboxContent className={styles['location-track-infobox__split']}>
                {splits.map((split, index) => {
                    return (
                        <Split
                            key={index.toString()}
                            location={getSplitLocation(split)}
                            isInitial={index === 0}
                            onRemove={removeSplit}
                        />
                    );
                })}
                <Endpoint
                    location={startAndEnd && locationTrackEndpointAddress(endPoint, startAndEnd)}
                />
                {splits.length <= 1 && (
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
