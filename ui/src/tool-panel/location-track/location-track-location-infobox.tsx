import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { LoaderStatus } from 'utils/react-utils';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import NavigableTrackMeter from 'geoviite-design-lib/track-meter/navigable-track-meter';
import { WriteAccessRequired } from 'user/write-access-required';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { getEndLinkPoints } from 'track-layout/layout-map-api';
import { LinkingAlignment, LinkingState, LinkingType, LinkInterval } from 'linking/linking-model';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import { EnvRestricted } from 'environment/env-restricted';
import { Precision, roundToPrecision } from 'utils/rounding';
import { formatToTM35FINString } from 'utils/geography-utils';
import Infobox from 'tool-panel/infobox/infobox';
import {
    LAYOUT_SRID,
    LayoutLocationTrack,
    LayoutSwitchId,
    LayoutTrackNumber,
} from 'track-layout/track-layout-model';
import {
    LocationTrackInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { SplitStart, SplittingState } from 'tool-panel/location-track/split-store';
import { ChangeTimes } from 'common/common-slice';
import { PublishType } from 'common/common-model';
import { useCommonDataAppSelector } from 'store/hooks';
import { getSplittingInitializationParameters } from 'track-layout/layout-location-track-api';
import { filterNotEmpty } from 'utils/array-utils';
import {
    useCoordinateSystem,
    useLocationTrackInfoboxExtras,
    useLocationTrackStartAndEnd,
} from 'track-layout/track-layout-react-utils';
import { createDelegates } from 'store/store-utils';
import { MapLayerName } from 'map/map-model';
import { updateLocationTrackGeometry } from 'linking/linking-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';

type LocationTrackLocationInfoboxContainerProps = {
    locationTrack: LayoutLocationTrack;
    trackNumber: LayoutTrackNumber | undefined;
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    linkingState: LinkingState | undefined;
    splittingState: SplittingState | undefined;
    publishType: PublishType;
};

export const LocationTrackLocationInfoboxContainer: React.FC<
    LocationTrackLocationInfoboxContainerProps
> = (props: LocationTrackLocationInfoboxContainerProps) => {
    const delegates = createDelegates(TrackLayoutActions);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    return (
        <LocationTrackLocationInfobox
            {...props}
            changeTimes={changeTimes}
            onStartSplitting={delegates.onStartSplitting}
            onStartLocationTrackGeometryChange={(interval: LinkInterval) => {
                delegates.showLayers(['alignment-linking-layer']);
                delegates.startAlignmentGeometryChange(interval);
            }}
            onEndLocationTrackGeometryChange={() => {
                delegates.hideLayers(['alignment-linking-layer']);
                delegates.stopLinking();
            }}
            showLayers={delegates.showLayers}
        />
    );
};

type LocationTrackLocationInfoboxProps = LocationTrackLocationInfoboxContainerProps & {
    changeTimes: ChangeTimes;
    onStartLocationTrackGeometryChange: (points: LinkInterval) => void;
    onEndLocationTrackGeometryChange: () => void;
    onStartSplitting: (splitStartParams: SplitStart) => void;
    showLayers: (layers: MapLayerName[]) => void;
};

export const LocationTrackLocationInfobox: React.FC<LocationTrackLocationInfoboxProps> = ({
    locationTrack,
    trackNumber,
    visibilities,
    visibilityChange,
    onStartLocationTrackGeometryChange,
    onEndLocationTrackGeometryChange,
    linkingState,
    splittingState,
    changeTimes,
    publishType,
    onStartSplitting,
    showLayers,
}: LocationTrackLocationInfoboxProps) => {
    const { t } = useTranslation();

    const isStartOrEndSwitch = (switchId: LayoutSwitchId) =>
        switchId === extraInfo?.switchAtStart?.id || switchId === extraInfo?.switchAtEnd?.id;

    const [startAndEndPoints, startAndEndPointFetchStatus] = useLocationTrackStartAndEnd(
        locationTrack?.id,
        publishType,
        changeTimes,
    );
    const coordinateSystem = useCoordinateSystem(LAYOUT_SRID);
    const [extraInfo, _] = useLocationTrackInfoboxExtras(
        locationTrack?.id,
        publishType,
        changeTimes,
    );

    const isDraft = locationTrack.draftType !== 'OFFICIAL';
    const duplicatesOnOtherTracks = extraInfo?.duplicates?.some(
        (dupe) => dupe.trackNumberId !== trackNumber?.id,
    );

    const getSplittingDisabledReasonsTranslated = () => {
        const reasons = [];
        if (isDraft)
            reasons.push(t('tool-panel.location-track.splitting.validation.track-draft-exists'));
        if (duplicatesOnOtherTracks)
            reasons.push(
                t(
                    'tool-panel.location-track.splitting.validation.duplicates-on-different-track-number',
                ),
            );
        return reasons.join('\n\n');
    };

    const [updatingLength, setUpdatingLength] = React.useState<boolean>(false);
    const [canUpdate, setCanUpdate] = React.useState<boolean>();

    React.useEffect(() => {
        setCanUpdate(
            linkingState?.type === LinkingType.LinkingAlignment && linkingState.state === 'allSet',
        );
    }, [linkingState]);

    const startSplitting = () => {
        getSplittingInitializationParameters('DRAFT', locationTrack.id).then(
            (splitInitializationParameters) => {
                if (startAndEndPoints?.start && startAndEndPoints?.end) {
                    onStartSplitting({
                        locationTrack: locationTrack,
                        allowedSwitches:
                            splitInitializationParameters?.switches.filter(
                                (sw) => !isStartOrEndSwitch(sw.switchId),
                            ) || [],
                        startAndEndSwitches: [
                            extraInfo?.switchAtStart?.id,
                            extraInfo?.switchAtEnd?.id,
                        ].filter(filterNotEmpty),
                        duplicateTracks: splitInitializationParameters?.duplicates || [],
                        startLocation: startAndEndPoints.start.point,
                        endLocation: startAndEndPoints.end.point,
                    });
                    showLayers(['location-track-split-location-layer']);
                }
            },
        );
    };

    const updateAlignment = (state: LinkingAlignment) => {
        if (canUpdate && state.layoutAlignmentInterval.start && state.layoutAlignmentInterval.end) {
            setUpdatingLength(true);
            updateLocationTrackGeometry(state.layoutAlignmentId, {
                min: state.layoutAlignmentInterval.start.m,
                max: state.layoutAlignmentInterval.end.m,
            })
                .then(() => {
                    Snackbar.success('tool-panel.location-track.location-track-endpoints-updated');
                    onEndLocationTrackGeometryChange();
                })
                .finally(() => setUpdatingLength(false));
        }
    };

    return (
        startAndEndPoints &&
        coordinateSystem && (
            <Infobox
                contentVisible={visibilities.location}
                onContentVisibilityChange={() => visibilityChange('location')}
                title={t('tool-panel.location-track.track-location-heading')}
                qa-id="location-track-location-infobox">
                <InfoboxContent>
                    <ProgressIndicatorWrapper
                        indicator={ProgressIndicatorType.Area}
                        inProgress={startAndEndPointFetchStatus !== LoaderStatus.Ready}>
                        <React.Fragment>
                            <InfoboxField
                                qaId="location-track-start-track-meter"
                                label={t('tool-panel.location-track.start-location')}>
                                {startAndEndPoints?.start?.address ? (
                                    <NavigableTrackMeter
                                        trackMeter={startAndEndPoints?.start?.address}
                                        location={startAndEndPoints?.start?.point}
                                    />
                                ) : (
                                    t('tool-panel.location-track.unset')
                                )}
                            </InfoboxField>
                            <InfoboxField
                                qaId="location-track-end-track-meter"
                                label={t('tool-panel.location-track.end-location')}>
                                {startAndEndPoints?.end?.address ? (
                                    <NavigableTrackMeter
                                        trackMeter={startAndEndPoints?.end?.address}
                                        location={startAndEndPoints?.end?.point}
                                    />
                                ) : (
                                    t('tool-panel.location-track.unset')
                                )}
                            </InfoboxField>

                            {linkingState === undefined && (
                                <WriteAccessRequired>
                                    {extraInfo?.partOfUnfinishedSplit && (
                                        <InfoboxContentSpread>
                                            <MessageBox>
                                                {t(
                                                    'tool-panel.alignment.geometry.part-of-unfinished-split',
                                                    {
                                                        locationTrackName: locationTrack.name,
                                                    },
                                                )}
                                            </MessageBox>
                                        </InfoboxContentSpread>
                                    )}
                                    <InfoboxButtons>
                                        <Button
                                            variant={ButtonVariant.SECONDARY}
                                            size={ButtonSize.SMALL}
                                            qa-id="modify-start-or-end"
                                            title={
                                                splittingState || extraInfo?.partOfUnfinishedSplit
                                                    ? t(
                                                          'tool-panel.location-track.splitting-blocks-geometry-changes',
                                                      )
                                                    : ''
                                            }
                                            disabled={
                                                !!splittingState ||
                                                extraInfo?.partOfUnfinishedSplit ||
                                                !startAndEndPoints.start?.point ||
                                                !startAndEndPoints.end?.point
                                            }
                                            onClick={() => {
                                                getEndLinkPoints(
                                                    locationTrack.id,
                                                    publishType,
                                                    'LOCATION_TRACK',
                                                    changeTimes.layoutLocationTrack,
                                                ).then(onStartLocationTrackGeometryChange);
                                            }}>
                                            {t('tool-panel.location-track.modify-start-or-end')}
                                        </Button>
                                    </InfoboxButtons>
                                </WriteAccessRequired>
                            )}
                            {linkingState?.type === LinkingType.LinkingAlignment && (
                                <React.Fragment>
                                    <p
                                        className={
                                            styles['location-track-infobox__link-alignment-guide']
                                        }>
                                        {t('tool-panel.location-track.choose-start-and-end-points')}
                                    </p>
                                    <InfoboxButtons>
                                        <Button
                                            variant={ButtonVariant.SECONDARY}
                                            size={ButtonSize.SMALL}
                                            disabled={updatingLength}
                                            onClick={onEndLocationTrackGeometryChange}>
                                            {t('button.cancel')}
                                        </Button>
                                        <Button
                                            size={ButtonSize.SMALL}
                                            isProcessing={updatingLength}
                                            disabled={updatingLength || !canUpdate}
                                            qa-id="save-start-and-end-changes"
                                            onClick={() => {
                                                updateAlignment(linkingState);
                                            }}>
                                            {t('tool-panel.location-track.ready')}
                                        </Button>
                                    </InfoboxButtons>
                                </React.Fragment>
                            )}
                            <EnvRestricted restrictTo="test">
                                <WriteAccessRequired>
                                    {isDraft && !extraInfo?.partOfUnfinishedSplit && (
                                        <InfoboxContentSpread>
                                            <MessageBox>
                                                {t(
                                                    'tool-panel.location-track.splitting.validation.track-draft-exists',
                                                )}
                                            </MessageBox>
                                        </InfoboxContentSpread>
                                    )}
                                    {duplicatesOnOtherTracks &&
                                        !extraInfo?.partOfUnfinishedSplit && (
                                            <InfoboxContentSpread>
                                                <MessageBox>
                                                    {t(
                                                        'tool-panel.location-track.splitting.validation.duplicates-on-different-track-number',
                                                    )}
                                                </MessageBox>
                                            </InfoboxContentSpread>
                                        )}
                                    <InfoboxButtons>
                                        {!linkingState && !splittingState && (
                                            <Button
                                                variant={ButtonVariant.SECONDARY}
                                                size={ButtonSize.SMALL}
                                                disabled={
                                                    locationTrack.draftType !== 'OFFICIAL' ||
                                                    duplicatesOnOtherTracks
                                                }
                                                title={getSplittingDisabledReasonsTranslated()}
                                                onClick={startSplitting}>
                                                {t('tool-panel.location-track.start-splitting')}
                                            </Button>
                                        )}
                                    </InfoboxButtons>
                                </WriteAccessRequired>
                            </EnvRestricted>

                            <InfoboxField
                                qaId="location-track-true-length"
                                label={t('tool-panel.location-track.true-length')}
                                value={
                                    roundToPrecision(
                                        locationTrack.length,
                                        Precision.alignmentLengthMeters,
                                    ) + ' m'
                                }
                            />
                            <InfoboxField
                                qaId="location-track-start-coordinates"
                                label={`${t('tool-panel.location-track.start-coordinates')} ${
                                    coordinateSystem.name
                                }`}
                                value={
                                    startAndEndPoints.start
                                        ? formatToTM35FINString(startAndEndPoints.start.point)
                                        : t('tool-panel.location-track.unset')
                                }
                            />
                            <InfoboxField
                                qaId="location-track-end-coordinates"
                                label={`${t('tool-panel.location-track.end-coordinates')} ${
                                    coordinateSystem.name
                                }`}
                                value={
                                    startAndEndPoints.end
                                        ? formatToTM35FINString(startAndEndPoints?.end.point)
                                        : t('tool-panel.location-track.unset')
                                }
                            />
                        </React.Fragment>
                    </ProgressIndicatorWrapper>
                </InfoboxContent>
            </Infobox>
        )
    );
};