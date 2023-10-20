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
    LocationTrackDescriptionSuffixMode,
    LocationTrackSplit,
} from 'track-layout/track-layout-model';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { formatTrackMeter } from 'utils/geography-utils';
import { Point } from 'model/geometry';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';

type LocationTrackInfoboxSplittingProps = {
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    splits: LocationTrackSplit[];
    endPoint: { address: TrackMeterModel };
    removeSplit: (startTrackMeter: TrackMeterModel) => void;
    addSplit: (split: { point: Point; address: TrackMeterModel }) => void;
};

type EndpointProps = {
    location: TrackMeterModel;
};

type SplitProps = EndpointProps & {
    isInitial: boolean;
    onRemove?: (startTrackMeter: TrackMeterModel) => void;
};

const Split: React.FC<SplitProps> = ({ location, isInitial, onRemove }) => {
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
                            onClick={() => onRemove && onRemove(location)}>
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
    visibilities,
    visibilityChange,
    splits,
    endPoint,
    removeSplit,
    addSplit,
}) => {
    const { t } = useTranslation();

    return (
        <Infobox
            contentVisible={visibilities.splitting}
            onContentVisibilityChange={() => visibilityChange('splitting')}
            title={'Raiteen jakaminen osiin'}>
            <InfoboxContent className={styles['location-track-infobox__split']}>
                {splits.map((split, index) => (
                    <Split
                        key={formatTrackMeter(split.location)}
                        location={split.location}
                        isInitial={index === 0}
                        onRemove={removeSplit}
                    />
                ))}
                <Endpoint location={endPoint.address} />
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
                        onClick={() => {
                            const prevSplit = splits[splits.length - 1];
                            addSplit({
                                point: { x: 0, y: 0 },
                                address: {
                                    ...prevSplit.location,
                                    meters: prevSplit.location.meters + 100,
                                },
                            });
                        }}>
                        MOAR
                    </Button>
                    <Button variant={ButtonVariant.SECONDARY} size={ButtonSize.SMALL}>
                        {t('button.cancel')}
                    </Button>
                    <Button size={ButtonSize.SMALL}>{t('Suorita jako')}</Button>
                </InfoboxButtons>
            </InfoboxContent>
        </Infobox>
    );
};
