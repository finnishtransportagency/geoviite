import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { useTranslation } from 'react-i18next';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import styles from './geometry-alignment-infobox.scss';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import {
    LayoutLocationTrack,
    LayoutReferenceLine,
    LayoutTrackNumberId,
    LocationTrackId,
    MapAlignment,
    MapAlignmentType,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import {
    getLinkedAlignmentIdsInPlan,
    getPlanLinkStatus,
    linkGeometryWithEmptyLocationTrack,
    linkGeometryWithEmptyReferenceLine,
    linkGeometryWithLocationTrack,
    linkGeometryWithReferenceLine,
} from 'linking/linking-api';
import { GeometryPlanId } from 'geometry/geometry-model';
import { PublishType, TimeStamp } from 'common/common-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { LocationTrackEditDialog } from 'tool-panel/location-track/dialog/location-track-edit-dialog';
import {
    getLocationTracks,
    getLocationTracksNear,
    getNonLinkedLocationTracks,
} from 'track-layout/layout-location-track-api';
import {
    getNonLinkedReferenceLines,
    getReferenceLine,
    getReferenceLinesNear,
} from 'track-layout/layout-reference-line-api';
import {
    LocationTrackBadge,
    LocationTrackBadgeStatus,
} from 'geoviite-design-lib/alignment/location-track-badge';
import LocationTrackTypeLabel from 'geoviite-design-lib/alignment/location-track-type-label';
import { deduplicateById, fieldComparator, filterNotEmpty, negComparator } from 'utils/array-utils';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import {
    GeometryLinkingAlignmentLockParameters,
    GeometryPreliminaryLinkingParameters,
    LinkingGeometryWithAlignment,
    LinkingGeometryWithAlignmentParameters,
    LinkingGeometryWithEmptyAlignment,
    LinkingGeometryWithEmptyAlignmentParameters,
    LinkingType,
    PreliminaryLinkingGeometry,
    toIntervalRequest,
} from 'linking/linking-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { LINKING_DOTS } from 'map/layers/layer-visibility-limits';
import {
    updateLocationTrackChangeTime,
    updateReferenceLineChangeTime,
    updateTrackNumberChangeTime,
} from 'common/change-time-api';
import {
    ReferenceLineBadge,
    ReferenceLineBadgeStatus,
} from 'geoviite-design-lib/alignment/reference-line-badge';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import LocationTrackNames from './location-track-names';
import { useLoader } from 'utils/react-utils';
import ReferenceLineNames from 'tool-panel/geometry-alignment/reference-line-names';
import { TrackNumberEditDialogContainer } from 'tool-panel/track-number/dialog/track-number-edit-dialog';
import { expandBoundingBox } from 'model/geometry';
import { OnSelectOptions } from 'selection/selection-model';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';

const NEAR_TRACK_SEARCH_BUFFER = 10.0;

function createLinkingGeometryWithAlignmentParameters(
    alignmentLinking: LinkingGeometryWithAlignment,
): LinkingGeometryWithAlignmentParameters {
    const layoutStart = alignmentLinking.layoutAlignmentInterval.start;
    const layoutEnd = alignmentLinking.layoutAlignmentInterval.end;
    const geometryStart = alignmentLinking.geometryAlignmentInterval.start;
    const geometryEnd = alignmentLinking.geometryAlignmentInterval.end;

    if (geometryStart && geometryEnd && layoutStart && layoutEnd) {
        return {
            geometryPlanId: alignmentLinking.geometryPlanId,
            geometryInterval: toIntervalRequest(geometryStart.alignmentId, geometryStart.m, geometryEnd.m),
            layoutInterval: toIntervalRequest(layoutStart.alignmentId, layoutStart.m, layoutEnd.m),
        };
    } else {
        throw Error('Cannot create linking parameters, mandatory information is missing!');
    }
}

function createLinkingGeometryWithEmptyAlignmentParameters(
    linking: LinkingGeometryWithEmptyAlignment,
): LinkingGeometryWithEmptyAlignmentParameters {
    const geometryStart = linking.geometryAlignmentInterval.start;
    const geometryEnd = linking.geometryAlignmentInterval.end;

    if (linking.layoutAlignmentId && geometryStart && geometryEnd) {
        return {
            geometryPlanId: linking.geometryPlanId,
            layoutAlignmentId: linking.layoutAlignmentId,
            geometryInterval: toIntervalRequest(geometryStart.alignmentId, geometryStart.m, geometryEnd.m),
        };
    } else {
        throw Error('Cannot create linking parameters, mandatory information is missing!');
    }
}

type GeometryAlignmentLinkingInfoboxProps = {
    onSelect: (options: OnSelectOptions) => void;
    geometryAlignment: MapAlignment;
    layoutLocationTrack: LayoutLocationTrack | undefined;
    layoutReferenceLine: LayoutReferenceLine | undefined;
    planId: GeometryPlanId;
    alignmentChangeTime: TimeStamp;
    trackNumberChangeTime: TimeStamp;
    linkingState?:
    | LinkingGeometryWithAlignment
    | LinkingGeometryWithEmptyAlignment
    | PreliminaryLinkingGeometry;
    onLockAlignment: (lockParameters: GeometryLinkingAlignmentLockParameters) => void;
    onLinkingStart: (startParams: GeometryPreliminaryLinkingParameters) => void;
    onStopLinking: () => void;
    resolution: number;
    publishType: PublishType;
};

type AlignmentRefs = {
    [key: string]: AlignmentSelection;
};
type AlignmentSelection = {
    key: string;
    id: LocationTrackId | ReferenceLineId;
    type: MapAlignmentType;
    segmentCount: number;
    ref: React.RefObject<HTMLLIElement>;
};

const referenceLineKey = (line: LayoutReferenceLine) => `RL_${line.id}`;

function createReferenceLineSelection(referenceLine: LayoutReferenceLine): AlignmentSelection {
    return {
        key: referenceLineKey(referenceLine),
        id: referenceLine.id,
        type: 'REFERENCE_LINE',
        segmentCount: referenceLine.segmentCount,
        ref: React.createRef(),
    };
}

const locationTrackKey = (track: LayoutLocationTrack) => `LT_${track.id}`;

function createLocationTrackSelection(locationTrack: LayoutLocationTrack): AlignmentSelection {
    return {
        key: locationTrackKey(locationTrack),
        id: locationTrack.id,
        type: 'LOCATION_TRACK',
        segmentCount: locationTrack.segmentCount,
        ref: React.createRef(),
    };
}

function getKey(
    referenceLine: LayoutReferenceLine | undefined,
    locationTrack: LayoutLocationTrack | undefined,
): string | undefined {
    if (locationTrack) return locationTrackKey(locationTrack);
    else if (referenceLine) return referenceLineKey(referenceLine);
    else return undefined;
}

const GeometryAlignmentLinkingInfobox: React.FC<GeometryAlignmentLinkingInfoboxProps> = ({
    onSelect,
    geometryAlignment,
    layoutLocationTrack,
    layoutReferenceLine,
    planId,
    alignmentChangeTime,
    trackNumberChangeTime,
    linkingState,
    onLinkingStart,
    onLockAlignment,
    onStopLinking,
    resolution,
    publishType,
}) => {
    const { t } = useTranslation();
    const [linkedAlignmentIds, setLinkedAlignmentIds] = React.useState<LocationTrackId[]>([]);
    const [showAddLocationTrackDialog, setShowAddLocationTrackDialog] = React.useState(false);
    const [showAddTrackNumberDialog, setShowAddTrackNumberDialog] = React.useState(false);

    const [alignmentRefs, setAlignmentRefs] = React.useState<AlignmentRefs>({});
    const [locationTracks, setLocationTracks] = React.useState<LayoutLocationTrack[]>([]);
    const [referenceLines, setReferenceLines] = React.useState<LayoutReferenceLine[]>([]);
    const linkingInProgress = linkingState?.state === 'setup' || linkingState?.state === 'allSet';
    const isLinked =
        geometryAlignment.sourceId && linkedAlignmentIds.includes(geometryAlignment.sourceId);
    const [linkingCallInProgress, setLinkingCallInProgress] = React.useState(false);
    const canLink = !linkingCallInProgress && linkingState?.state == 'allSet';

    const trackNumbers = useTrackNumbers(publishType, trackNumberChangeTime);

    const planStatus = useLoader(
        () => (planId ? getPlanLinkStatus(planId, publishType) : undefined),
        [planId, alignmentChangeTime, publishType],
    );

    const linkedReferenceLines = useLoader(() => {
        if (!planStatus) return undefined;
        const referenceLineIds = planStatus.alignments
            .filter((linkStatus) => linkStatus.id === geometryAlignment.sourceId)
            .flatMap((linkStatus) => linkStatus.linkedReferenceLineIds);
        const referenceLinePromises = referenceLineIds.map((referenceLineId) =>
            getReferenceLine(referenceLineId, publishType),
        );
        return Promise.all(referenceLinePromises).then((lines) => lines.filter(filterNotEmpty));
    }, [planStatus, geometryAlignment]);

    const linkedLocationTracks = useLoader(() => {
        if (!planStatus) return undefined;
        const locationTrackIds = planStatus.alignments
            .filter((linkStatus) => linkStatus.id === geometryAlignment.sourceId)
            .flatMap((linkStatus) => linkStatus.linkedLocationTrackIds);
        return getLocationTracks(locationTrackIds, publishType);
    }, [planStatus, geometryAlignment]);

    const canLockAlignment = () => {
        const key = getKey(layoutReferenceLine, layoutLocationTrack);
        return !!(key && alignmentRefs[key]);
    };

    const onReferenceLineSelect = (rl: LayoutReferenceLine) => {
        onSelect({
            trackNumbers: [rl.trackNumberId],
            locationTracks: [],
        });
    };

    const onLocationTrackSelect = (lt: LayoutLocationTrack) =>
        onSelect({
            trackNumbers: [],
            locationTracks: [lt.id],
        });

    React.useEffect(() => {
        getLinkedAlignmentIdsInPlan(planId, publishType).then((linkedIds) => {
            setLinkedAlignmentIds(linkedIds);
        });
    }, [planId, publishType, alignmentChangeTime]);

    React.useEffect(() => {
        const tracks = locationTracks.map((lt) => createLocationTrackSelection(lt));
        if (layoutLocationTrack && !locationTracks.some((lt) => lt.id === layoutLocationTrack.id)) {
            tracks.push(createLocationTrackSelection(layoutLocationTrack));
        }
        const lines = referenceLines.map((rl) => createReferenceLineSelection(rl));
        if (layoutReferenceLine && !referenceLines.some((lt) => lt.id === layoutReferenceLine.id)) {
            tracks.push(createReferenceLineSelection(layoutReferenceLine));
        }
        const newRefs = [...tracks, ...lines].reduce((acc: AlignmentRefs, value) => {
            acc[value.key] = value;
            return acc;
        }, {});
        setAlignmentRefs(newRefs);
    }, [layoutLocationTrack, layoutReferenceLine, locationTracks, referenceLines]);

    React.useEffect(() => {
        const selectedRef = getKey(layoutReferenceLine, layoutLocationTrack);
        if (selectedRef && alignmentRefs[selectedRef]) {
            alignmentRefs[selectedRef].ref.current?.scrollIntoView({
                behavior: 'smooth',
                block: 'nearest',
            });
        }
    }, [layoutReferenceLine, layoutLocationTrack]);

    React.useEffect(() => {
        if (geometryAlignment.boundingBox) {
            Promise.all([
                getNonLinkedReferenceLines().then((lines) =>
                    lines.sort(negComparator(fieldComparator((line) => line.id))),
                ),
                getReferenceLinesNear(
                    publishType,
                    expandBoundingBox(geometryAlignment.boundingBox, NEAR_TRACK_SEARCH_BUFFER),
                ),
            ]).then((lineGroups) => {
                setReferenceLines(deduplicateById(lineGroups.flat(), (l) => l.id));
            });
        }
    }, [geometryAlignment.boundingBox, alignmentChangeTime, trackNumberChangeTime]);

    React.useEffect(() => {
        if (geometryAlignment.boundingBox) {
            Promise.all([
                getNonLinkedLocationTracks().then((tracks) =>
                    tracks.sort(negComparator(fieldComparator((a) => a.id))),
                ),
                getLocationTracksNear(
                    publishType,
                    expandBoundingBox(geometryAlignment.boundingBox, NEAR_TRACK_SEARCH_BUFFER),
                ),
            ]).then((trackGroups) => {
                setLocationTracks(deduplicateById(trackGroups.flat(), (l) => l.id));
            });
        }
    }, [geometryAlignment.boundingBox, alignmentChangeTime]);

    function handleLocationTrackInsert(_id: LocationTrackId) {
        updateLocationTrackChangeTime();
    }

    function handleTrackNumberSave(_id: LayoutTrackNumberId) {
        updateReferenceLineChangeTime().then(() => updateTrackNumberChangeTime());
    }

    function lockAlignment() {
        if (canLockAlignment()) {
            const key = getKey(layoutReferenceLine, layoutLocationTrack) as string;
            const selection = alignmentRefs[key];
            onLockAlignment({
                alignmentId: selection.id,
                alignmentType: selection.type,
                type:
                    selection.segmentCount > 0
                        ? LinkingType.LinkingGeometryWithAlignment
                        : LinkingType.LinkingGeometryWithEmptyAlignment,
            });
        }
    }

    function startLinking() {
        onLinkingStart({
            geometryPlanId: planId,
            geometryAlignmentId: geometryAlignment.id,
        });
    }

    async function link() {
        if (!canLink) {
            return;
        }

        setLinkingCallInProgress(true);
        try {
            if (linkingState?.type === LinkingType.LinkingGeometryWithAlignment) {
                const linkingParameters =
                    createLinkingGeometryWithAlignmentParameters(linkingState);
                const result = await (linkingState.layoutAlignmentType == 'LOCATION_TRACK'
                    ? linkGeometryWithLocationTrack(linkingParameters)
                    : linkGeometryWithReferenceLine(linkingParameters));
                if (result) {
                    Snackbar.success(
                        t('tool-panel.alignment.geometry.linking-succeeded-and-previous-unlinked'),
                    );
                    onStopLinking();
                } else {
                    Snackbar.error(t('error.linking.generic'));
                }
            } else if (linkingState?.type == LinkingType.LinkingGeometryWithEmptyAlignment) {
                const linkingParameters =
                    createLinkingGeometryWithEmptyAlignmentParameters(linkingState);
                const result = await (linkingState.layoutAlignmentType == 'LOCATION_TRACK'
                    ? linkGeometryWithEmptyLocationTrack(linkingParameters)
                    : linkGeometryWithEmptyReferenceLine(linkingParameters));
                if (result) {
                    Snackbar.success(t('tool-panel.alignment.geometry.linking-succeeded'));
                    onStopLinking();
                } else {
                    Snackbar.error(t('error.linking.generic'));
                }
            }
        } finally {
            setLinkingCallInProgress(false);
        }
    }

    return (
        <React.Fragment>
            <Infobox
                title={t('tool-panel.alignment.geometry.linking-title')}
                qa-id="geometry-alignment-linking-infobox">
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.alignment.geometry.is-linked')}
                        className={styles['geometry-alignment-infobox__linked-status']}
                        value={
                            isLinked ? (
                                <span className={styles['geometry-alignment-infobox__linked-text']}>
                                    {t('yes')}
                                </span>
                            ) : (
                                <span
                                    className={
                                        styles['geometry-alignment-infobox__not-linked-text']
                                    }>
                                    {t('no')}
                                </span>
                            )
                        }
                    />

                    {linkedLocationTracks && linkedLocationTracks.length > 0 && (
                        <LocationTrackNames linkedLocationTracks={linkedLocationTracks} />
                    )}
                    {linkedReferenceLines && linkedReferenceLines.length > 0 && (
                        <ReferenceLineNames
                            linkedReferenceLines={linkedReferenceLines}
                            publishType={publishType}
                            alignmentChangeTime={alignmentChangeTime}
                        />
                    )}

                    {linkingState && (
                        <div className={styles['geometry-alignment-infobox__linking-container']}>
                            <div className={styles['geometry-alignment-infobox__search-container']}>
                                <InfoboxText
                                    value={t('tool-panel.alignment.geometry.choose-reference-line')}
                                />
                                <Button
                                    variant={ButtonVariant.GHOST}
                                    size={ButtonSize.SMALL}
                                    icon={Icons.Append}
                                    onClick={() => setShowAddTrackNumberDialog(true)}
                                    qa-id="create-tracknumer-button"
                                />
                            </div>
                            <ul
                                className={
                                    styles['geometry-alignment-infobox__alignments-container']
                                }>
                                {referenceLines &&
                                    referenceLines.map((line) => {
                                        const key = getKey(line, undefined);
                                        const isSelected =
                                            key ===
                                            getKey(layoutReferenceLine, layoutLocationTrack);
                                        const selection = key ? alignmentRefs[key] : undefined;
                                        const trackNumber = trackNumbers
                                            ? trackNumbers.find(
                                                (tn) => tn.id === line.trackNumberId,
                                            )
                                            : undefined;
                                        if (!selection || !trackNumber) return '';
                                        return (
                                            <li
                                                key={selection.key}
                                                className={
                                                    styles['geometry-alignment-infobox__alignment']
                                                }
                                                onClick={() => onReferenceLineSelect(line)}
                                                ref={selection.ref}>
                                                <ReferenceLineBadge
                                                    trackNumber={trackNumber}
                                                    status={
                                                        isSelected
                                                            ? ReferenceLineBadgeStatus.SELECTED
                                                            : ReferenceLineBadgeStatus.DEFAULT
                                                    }
                                                />
                                                {linkingInProgress &&
                                                    linkingState.layoutAlignmentId === line.id && (
                                                        <Icons.Lock
                                                            size={IconSize.SMALL}
                                                            color={IconColor.INHERIT}
                                                        />
                                                    )}
                                            </li>
                                        );
                                    })}
                            </ul>
                            <div className={styles['geometry-alignment-infobox__search-container']}>
                                <InfoboxText
                                    value={t('tool-panel.alignment.geometry.choose-location-track')}
                                />
                                <Button
                                    variant={ButtonVariant.GHOST}
                                    size={ButtonSize.SMALL}
                                    icon={Icons.Append}
                                    onClick={() => setShowAddLocationTrackDialog(true)}
                                    qa-id="create-location-track-button"
                                />
                            </div>
                            <ul
                                className={
                                    styles['geometry-alignment-infobox__alignments-container']
                                }>
                                {locationTracks &&
                                    locationTracks.map((track) => {
                                        const key = getKey(undefined, track);
                                        const isSelected =
                                            key ===
                                            getKey(layoutReferenceLine, layoutLocationTrack);
                                        const selection = key ? alignmentRefs[key] : undefined;
                                        if (!selection) return '';
                                        return (
                                            <li
                                                key={selection.key}
                                                className={
                                                    styles['geometry-alignment-infobox__alignment']
                                                }
                                                onClick={() => onLocationTrackSelect(track)}
                                                ref={selection.ref}>
                                                <LocationTrackBadge
                                                    locationTrack={track}
                                                    status={
                                                        isSelected
                                                            ? LocationTrackBadgeStatus.SELECTED
                                                            : LocationTrackBadgeStatus.DEFAULT
                                                    }
                                                />
                                                {linkingInProgress &&
                                                    linkingState.layoutAlignmentId === track.id && (
                                                        <Icons.Lock
                                                            size={IconSize.SMALL}
                                                            color={IconColor.INHERIT}
                                                        />
                                                    )}
                                                <span>
                                                    <LocationTrackTypeLabel type={track.type} />
                                                </span>
                                            </li>
                                        );
                                    })}
                            </ul>
                        </div>
                    )}
                    {linkingInProgress && (
                        <React.Fragment>
                            <div
                                className={styles['geometry-alignment-infobox__connection-points']}>
                                {t('tool-panel.alignment.geometry.choose-connection-points')}
                            </div>
                            {resolution > LINKING_DOTS && (
                                <InfoboxContentSpread>
                                    <MessageBox>
                                        {t('tool-panel.alignment.geometry.zoom-closer')}
                                    </MessageBox>
                                </InfoboxContentSpread>
                            )}
                            {linkingState.errors.map((errorKey) => (
                                <InfoboxContentSpread key={errorKey}>
                                    <MessageBox>{t(errorKey)}</MessageBox>
                                </InfoboxContentSpread>
                            ))}
                        </React.Fragment>
                    )}

                    {linkingState === undefined && (
                        <InfoboxButtons>
                            <Button size={ButtonSize.SMALL} onClick={startLinking}>
                                {t(
                                    `tool-panel.alignment.geometry.${isLinked ? 'add-linking' : 'start-setup'
                                    }`,
                                )}
                            </Button>
                        </InfoboxButtons>
                    )}
                    {linkingState?.state === 'preliminary' && (
                        <InfoboxButtons>
                            <Button
                                size={ButtonSize.SMALL}
                                variant={ButtonVariant.SECONDARY}
                                onClick={onStopLinking}>
                                {t('tool-panel.alignment.geometry.cancel')}
                            </Button>
                            <Button
                                size={ButtonSize.SMALL}
                                disabled={!canLockAlignment()}
                                onClick={lockAlignment}>
                                {t('tool-panel.alignment.geometry.lock-location-track')}
                            </Button>
                        </InfoboxButtons>
                    )}
                    {linkingInProgress && (
                        <InfoboxButtons>
                            <Button
                                size={ButtonSize.SMALL}
                                variant={ButtonVariant.SECONDARY}
                                disabled={linkingCallInProgress}
                                onClick={startLinking}>
                                {t('tool-panel.alignment.geometry.return')}
                            </Button>
                            <Button
                                size={ButtonSize.SMALL}
                                isProcessing={linkingCallInProgress}
                                disabled={!canLink}
                                onClick={link}>
                                {t('tool-panel.alignment.geometry.save-link')}
                            </Button>
                        </InfoboxButtons>
                    )}
                </InfoboxContent>
            </Infobox>

            {showAddLocationTrackDialog && (
                <LocationTrackEditDialog
                    onClose={() => setShowAddLocationTrackDialog(false)}
                    onInsert={handleLocationTrackInsert}
                    locationTrackChangeTime={alignmentChangeTime}
                    publishType={publishType}
                />
            )}
            {showAddTrackNumberDialog && (
                <TrackNumberEditDialogContainer
                    onClose={() => setShowAddTrackNumberDialog(false)}
                    onSave={handleTrackNumberSave}
                />
            )}
        </React.Fragment>
    );
};

export default GeometryAlignmentLinkingInfobox;
