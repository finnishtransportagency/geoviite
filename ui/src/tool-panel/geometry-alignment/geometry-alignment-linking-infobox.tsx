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
import { LocationTrackEditDialog } from 'tool-panel/location-track/dialog/location-track-edit-dialog';
import { getLocationTracks } from 'track-layout/layout-location-track-api';
import { getReferenceLine } from 'track-layout/layout-reference-line-api';
import { filterNotEmpty } from 'utils/array-utils';
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
import { LINKING_DOTS } from 'map/layers/utils/layer-visibility-limits';
import {
    updateLocationTrackChangeTime,
    updateReferenceLineChangeTime,
    updateTrackNumberChangeTime,
} from 'common/change-time-api';
import LocationTrackNames from './location-track-names';
import { useLoader } from 'utils/react-utils';
import ReferenceLineNames from 'tool-panel/geometry-alignment/reference-line-names';
import { TrackNumberEditDialogContainer } from 'tool-panel/track-number/dialog/track-number-edit-dialog';
import { OnSelectOptions } from 'selection/selection-model';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import {
    GeometryAlignmentLinkingLocationTrackCandidates,
    GeometryAlignmentLinkingReferenceLineCandidates,
} from 'tool-panel/geometry-alignment/geometry-alignment-linking-candidates';
import { WriteAccessRequired } from 'user/write-access-required';
import { AlignmentHeader } from 'track-layout/layout-map-api';

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
            geometryInterval: toIntervalRequest(
                geometryStart.alignmentId,
                geometryStart.m,
                geometryEnd.m,
            ),
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
            geometryInterval: toIntervalRequest(
                geometryStart.alignmentId,
                geometryStart.m,
                geometryEnd.m,
            ),
        };
    } else {
        throw Error('Cannot create linking parameters, mandatory information is missing!');
    }
}

type GeometryAlignmentLinkingInfoboxProps = {
    onSelect: (options: OnSelectOptions) => void;
    geometryAlignment: AlignmentHeader;
    selectedLayoutLocationTrack?: LayoutLocationTrack;
    selectedLayoutReferenceLine?: LayoutReferenceLine;
    planId: GeometryPlanId;
    locationTrackChangeTime: TimeStamp;
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
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
};

const GeometryAlignmentLinkingInfobox: React.FC<GeometryAlignmentLinkingInfoboxProps> = ({
    onSelect,
    geometryAlignment,
    selectedLayoutLocationTrack,
    selectedLayoutReferenceLine,
    planId,
    locationTrackChangeTime,
    trackNumberChangeTime,
    linkingState,
    onLinkingStart,
    onLockAlignment,
    onStopLinking,
    resolution,
    publishType,
    contentVisible,
    onContentVisibilityChange,
}) => {
    const { t } = useTranslation();
    const [linkedAlignmentIds, setLinkedAlignmentIds] = React.useState<LocationTrackId[]>([]);
    const [showAddLocationTrackDialog, setShowAddLocationTrackDialog] = React.useState(false);
    const [showAddTrackNumberDialog, setShowAddTrackNumberDialog] = React.useState(false);

    const linkingInProgress = linkingState?.state === 'setup' || linkingState?.state === 'allSet';
    const isLinked = geometryAlignment.id && linkedAlignmentIds.includes(geometryAlignment.id);
    const [linkingCallInProgress, setLinkingCallInProgress] = React.useState(false);
    const canLink = !linkingCallInProgress && linkingState?.state == 'allSet';

    const planStatus = useLoader(
        () => (planId ? getPlanLinkStatus(planId, publishType) : undefined),
        [planId, locationTrackChangeTime, publishType],
    );

    const linkedReferenceLines = useLoader(() => {
        if (!planStatus) return undefined;
        const referenceLineIds = planStatus.alignments
            .filter((linkStatus) => linkStatus.id === geometryAlignment.id)
            .flatMap((linkStatus) => linkStatus.linkedReferenceLineIds);
        const referenceLinePromises = referenceLineIds.map((referenceLineId) =>
            getReferenceLine(referenceLineId, publishType),
        );
        return Promise.all(referenceLinePromises).then((lines) => lines.filter(filterNotEmpty));
    }, [planStatus, geometryAlignment]);

    const linkedLocationTracks = useLoader(() => {
        if (!planStatus) return undefined;
        const locationTrackIds = planStatus.alignments
            .filter((linkStatus) => linkStatus.id === geometryAlignment.id)
            .flatMap((linkStatus) => linkStatus.linkedLocationTrackIds);
        return getLocationTracks(locationTrackIds, publishType);
    }, [planStatus, geometryAlignment]);

    const canLockAlignment = !!(selectedLayoutReferenceLine || selectedLayoutLocationTrack);

    React.useEffect(() => {
        getLinkedAlignmentIdsInPlan(planId, publishType).then((linkedIds) => {
            setLinkedAlignmentIds(linkedIds);
        });
    }, [planId, publishType, locationTrackChangeTime]);

    function handleLocationTrackInsert(id: LocationTrackId) {
        onSelect({ locationTracks: [id] });
        updateLocationTrackChangeTime();
    }

    function handleTrackNumberSave(id: LayoutTrackNumberId) {
        onSelect({ trackNumbers: [id] });
        updateReferenceLineChangeTime().then(() => updateTrackNumberChangeTime());
    }

    function lockAlignment() {
        const selectedAlignment = selectedLayoutLocationTrack || selectedLayoutReferenceLine;

        if (selectedAlignment) {
            onLockAlignment({
                alignmentId: selectedAlignment.id,
                alignmentType: selectedLayoutLocationTrack ? 'LOCATION_TRACK' : 'REFERENCE_LINE',
                type:
                    selectedAlignment.segmentCount > 0
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
                qa-id="geometry-alignment-linking-infobox"
                contentVisible={contentVisible}
                onContentVisibilityChange={onContentVisibilityChange}>
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
                            alignmentChangeTime={locationTrackChangeTime}
                        />
                    )}

                    {linkingState && (
                        <div className={styles['geometry-alignment-infobox__linking-container']}>
                            <GeometryAlignmentLinkingReferenceLineCandidates
                                geometryAlignment={geometryAlignment}
                                publishType={publishType}
                                trackNumberChangeTime={trackNumberChangeTime}
                                onSelect={onSelect}
                                selectedLayoutReferenceLine={selectedLayoutReferenceLine}
                                disableAddButton={
                                    linkingState.type !== LinkingType.UnknownAlignment
                                }
                                onShowAddTrackNumberDialog={() => setShowAddTrackNumberDialog(true)}
                            />
                            <GeometryAlignmentLinkingLocationTrackCandidates
                                geometryAlignment={geometryAlignment}
                                publishType={publishType}
                                locationTrackChangeTime={locationTrackChangeTime}
                                onSelect={onSelect}
                                selectedLayoutLocationTrack={selectedLayoutLocationTrack}
                                disableAddButton={
                                    linkingState.type !== LinkingType.UnknownAlignment
                                }
                                onShowAddLocationTrackDialog={() =>
                                    setShowAddLocationTrackDialog(true)
                                }
                            />
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
                        <WriteAccessRequired>
                            <InfoboxButtons>
                                <Button size={ButtonSize.SMALL} onClick={startLinking}>
                                    {t(
                                        `tool-panel.alignment.geometry.${
                                            isLinked ? 'add-linking' : 'start-setup'
                                        }`,
                                    )}
                                </Button>
                            </InfoboxButtons>
                        </WriteAccessRequired>
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
                                disabled={!canLockAlignment}
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
                    locationTrackChangeTime={locationTrackChangeTime}
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
