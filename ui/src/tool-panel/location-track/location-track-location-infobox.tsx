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
import { draftLayoutContext, LayoutContext } from 'common/common-model';
import { useCommonDataAppSelector } from 'store/hooks';
import {
    getSplittingInitializationParameters,
    SplitDuplicate,
} from 'track-layout/layout-location-track-api';
import {
    useCoordinateSystem,
    useLocationTrackInfoboxExtras,
    useLocationTrackStartAndEnd,
} from 'track-layout/track-layout-react-utils';
import { createDelegates } from 'store/store-utils';
import { MapLayerName } from 'map/map-model';
import { updateLocationTrackGeometry } from 'linking/linking-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_LAYOUT } from 'user/user-model';
import { getSplitPointName } from 'tool-panel/location-track/splitting/location-track-splitting-infobox';

type LocationTrackLocationInfoboxContainerProps = {
    locationTrack: LayoutLocationTrack;
    trackNumber: LayoutTrackNumber | undefined;
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    linkingState: LinkingState | undefined;
    splittingState: SplittingState | undefined;
    layoutContext: LayoutContext;
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
    layoutContext,
    onStartSplitting,
}: LocationTrackLocationInfoboxProps) => {
    const { t } = useTranslation();
    const [startAndEndPoints, startAndEndPointFetchStatus] = useLocationTrackStartAndEnd(
        locationTrack?.id,
        layoutContext,
        changeTimes,
    );
    const coordinateSystem = useCoordinateSystem(LAYOUT_SRID);
    const [extraInfo, _] = useLocationTrackInfoboxExtras(
        locationTrack?.id,
        layoutContext,
        changeTimes,
    );

    const isDraft = layoutContext.publicationState === 'DRAFT';
    const locationTrackIsDraft = locationTrack.editState !== 'UNEDITED';
    const duplicatesOnOtherTrackNumbers = extraInfo?.duplicates?.some(
        (duplicate) => duplicate.trackNumberId !== trackNumber?.id,
    );

    const getSplittingDisabledReasonsTranslated = () => {
        const reasons: string[] = [];

        if (!isDraft) {
            return t('tool-panel.disabled.activity-disabled-in-official-mode');
        }

        if (extraInfo?.partOfUnfinishedSplit) {
            return t('tool-panel.location-track.splitting-blocks-geometry-changes');
        }

        if (locationTrack.state !== 'IN_USE') {
            return t('tool-panel.location-track.unsupported-state-for-splitting');
        }

        if (locationTrackIsDraft)
            reasons.push(t('tool-panel.location-track.splitting.validation.track-draft-exists'));
        if (duplicatesOnOtherTrackNumbers)
            reasons.push(
                t(
                    'tool-panel.location-track.splitting.validation.duplicates-on-different-track-number',
                ),
            );

        return reasons.join('\n\n');
    };

    const getModifyStartOrEndDisabledReasonTranslated = () => {
        if (!isDraft) {
            return t('tool-panel.disabled.activity-disabled-in-official-mode');
        } else if (splittingState || extraInfo?.partOfUnfinishedSplit) {
            return t('tool-panel.location-track.splitting-blocks-geometry-changes');
        } else {
            return undefined;
        }
    };

    const [updatingLength, setUpdatingLength] = React.useState<boolean>(false);
    const [canUpdate, setCanUpdate] = React.useState<boolean>();
    const [startingSplitting, setStartingSplitting] = React.useState<boolean>(false);

    React.useEffect(() => {
        setCanUpdate(
            linkingState?.type === LinkingType.LinkingAlignment && linkingState.state === 'allSet',
        );
    }, [linkingState]);

    const startSplitting = () => {
        setStartingSplitting(true);
        getSplittingInitializationParameters(draftLayoutContext(layoutContext), locationTrack.id)
            .then((splitInitializationParameters) => {
                if (
                    startAndEndPoints?.start &&
                    startAndEndPoints?.end &&
                    trackNumber &&
                    extraInfo
                ) {
                    const switches = splitInitializationParameters?.switches || [];
                    const getSwitchName = (switchId: LayoutSwitchId) =>
                        switches.find((sw) => sw.switchId == switchId)?.name;
                    const endPointTerm = t('tool-panel.location-track.splitting.endpoint');
                    const noNameErrorTerm = 'ERROR: no name';
                    const startSplitPointName =
                        getSplitPointName(extraInfo.startSplitPoint, getSwitchName, endPointTerm) ||
                        noNameErrorTerm;
                    const endSplitPointName =
                        getSplitPointName(extraInfo.endSplitPoint, getSwitchName, endPointTerm) ||
                        noNameErrorTerm;

                    const duplicates = splitInitializationParameters?.duplicates || [];
                    const duplicatesWithNames: SplitDuplicate[] = duplicates.map((duplicate) => {
                        return {
                            ...duplicate,
                            status: {
                                ...duplicate.status,
                                startSplitPoint: duplicate.status.startSplitPoint && {
                                    ...duplicate.status.startSplitPoint,
                                    name:
                                        getSplitPointName(
                                            duplicate.status.startSplitPoint,
                                            getSwitchName,
                                            endPointTerm,
                                        ) || noNameErrorTerm,
                                },
                                endSplitPoint: duplicate.status.endSplitPoint && {
                                    ...duplicate.status.endSplitPoint,
                                    name:
                                        getSplitPointName(
                                            duplicate.status.endSplitPoint,
                                            getSwitchName,
                                            endPointTerm,
                                        ) || noNameErrorTerm,
                                },
                            },
                        };
                    });

                    onStartSplitting({
                        locationTrack: locationTrack,
                        trackSwitches: splitInitializationParameters?.switches || [],
                        duplicateTracks: duplicatesWithNames,
                        startLocation: startAndEndPoints.start.point,
                        endLocation: startAndEndPoints.end.point,
                        trackNumber: trackNumber.number,
                        startSplitPoint: {
                            ...extraInfo.startSplitPoint,
                            name: startSplitPointName,
                        },
                        endSplitPoint: {
                            ...extraInfo.endSplitPoint,
                            name: endSplitPointName,
                        },
                    });
                }
            })
            .finally(() => setStartingSplitting(false));
    };

    const updateAlignment = (state: LinkingAlignment) => {
        if (
            canUpdate &&
            state.layoutAlignmentInterval.start &&
            state.layoutAlignmentInterval.end &&
            state.layoutAlignment.type === 'LOCATION_TRACK'
        ) {
            setUpdatingLength(true);
            updateLocationTrackGeometry(state.layoutAlignment.id, {
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
                                <PrivilegeRequired privilege={EDIT_LAYOUT}>
                                    {isDraft && extraInfo?.partOfUnfinishedSplit && (
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
                                            title={getModifyStartOrEndDisabledReasonTranslated()}
                                            disabled={
                                                layoutContext.publicationState !== 'DRAFT' ||
                                                !!splittingState ||
                                                extraInfo?.partOfUnfinishedSplit ||
                                                !startAndEndPoints.start?.point ||
                                                !startAndEndPoints.end?.point ||
                                                startingSplitting
                                            }
                                            onClick={() => {
                                                getEndLinkPoints(
                                                    locationTrack.id,
                                                    layoutContext,
                                                    'LOCATION_TRACK',
                                                    changeTimes.layoutLocationTrack,
                                                ).then(onStartLocationTrackGeometryChange);
                                            }}>
                                            {t('tool-panel.location-track.modify-start-or-end')}
                                        </Button>
                                    </InfoboxButtons>
                                </PrivilegeRequired>
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
                                <PrivilegeRequired privilege={EDIT_LAYOUT}>
                                    {isDraft &&
                                        locationTrackIsDraft &&
                                        !extraInfo?.partOfUnfinishedSplit && (
                                            <InfoboxContentSpread>
                                                <MessageBox>
                                                    {t(
                                                        'tool-panel.location-track.splitting.validation.track-draft-exists',
                                                    )}
                                                </MessageBox>
                                            </InfoboxContentSpread>
                                        )}
                                    {isDraft &&
                                        duplicatesOnOtherTrackNumbers &&
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
                                                    locationTrack.state !== 'IN_USE' ||
                                                    !isDraft ||
                                                    locationTrackIsDraft ||
                                                    duplicatesOnOtherTrackNumbers ||
                                                    extraInfo?.partOfUnfinishedSplit ||
                                                    startingSplitting
                                                }
                                                isProcessing={startingSplitting}
                                                title={getSplittingDisabledReasonsTranslated()}
                                                onClick={startSplitting}
                                                qa-id="start-splitting">
                                                {t('tool-panel.location-track.start-splitting')}
                                            </Button>
                                            /* TODO: Uncomment once splitting with prefilled data is implemented
                                            <SplitButton
                                                variant={ButtonVariant.SECONDARY}
                                                size={ButtonSize.SMALL}
                                                disabled={
                                                    locationTrack.state !== 'IN_USE' ||
                                                    !isDraft ||
                                                    locationTrackIsDraft ||
                                                    duplicatesOnOtherTrackNumbers ||
                                                    extraInfo?.partOfUnfinishedSplit ||
                                                    startingSplitting
                                                }
                                                isProcessing={startingSplitting}
                                                title={getSplittingDisabledReasonsTranslated()}
                                                onClick={startSplitting}
                                                qaId={'start-splitting'}
                                                menuItems={[
                                                    menuSelectOption(
                                                        () => {
                                                            startSplitting();
                                                        },
                                                        t(
                                                            'tool-panel.location-track.start-splitting-prefilled',
                                                        ),
                                                        'start-splitting-prefilled',
                                                    ),
                                                ]}>
                                                {t('tool-panel.location-track.start-splitting')}
                                            </SplitButton>*/
                                        )}
                                    </InfoboxButtons>
                                </PrivilegeRequired>
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
