import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { useTranslation } from 'react-i18next';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import styles from './geometry-alignment-infobox.scss';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import {
    LayoutLocationTrack,
    LayoutReferenceLine,
    MapAlignmentType,
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
import { draftLayoutContext, LayoutContext } from 'common/common-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { LocationTrackEditDialogContainer } from 'tool-panel/location-track/dialog/location-track-edit-dialog';
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
    LinkingPhase,
    LinkingType,
    PreliminaryLinkingGeometry,
    toIntervalRequest,
} from 'linking/linking-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { LINKING_DOTS } from 'map/layers/utils/layer-visibility-limits';
import LocationTrackNames from './location-track-names';
import { LoaderStatus, useLoader, useLoaderWithStatus } from 'utils/react-utils';
import ReferenceLineNames from 'tool-panel/geometry-alignment/reference-line-names';
import { TrackNumberEditDialogContainer } from 'tool-panel/track-number/dialog/track-number-edit-dialog';
import { OnSelectOptions, OptionalUnselectableItemCollections } from 'selection/selection-model';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import {
    GeometryAlignmentLinkingLocationTrackCandidates,
    GeometryAlignmentLinkingReferenceLineCandidates,
} from 'tool-panel/geometry-alignment/geometry-alignment-linking-candidates';
import { GeometryAlignmentHeader } from 'track-layout/layout-map-api';
import {
    refreshLocationTrackSelection,
    refreshTrackNumberSelection,
    useLocationTrackInfoboxExtras,
} from 'track-layout/track-layout-react-utils';
import { Radio } from 'vayla-design-lib/radio/radio';
import { ChangeTimes } from 'common/common-slice';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_LAYOUT } from 'user/user-model';
import { LinkingStatusLabel } from 'geoviite-design-lib/linking-status/linking-status-label';
import { Spinner } from 'vayla-design-lib/spinner/spinner';

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

    if (linking.layoutAlignment.id && geometryStart && geometryEnd) {
        return {
            geometryPlanId: linking.geometryPlanId,
            layoutAlignmentId: linking.layoutAlignment.id,
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
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    geometryAlignment: GeometryAlignmentHeader;
    selectedLayoutLocationTrack?: LayoutLocationTrack;
    selectedLayoutReferenceLine?: LayoutReferenceLine;
    planId: GeometryPlanId;
    changeTimes: ChangeTimes;
    linkingState?:
        | LinkingGeometryWithAlignment
        | LinkingGeometryWithEmptyAlignment
        | PreliminaryLinkingGeometry;
    onLockAlignment: (lockParameters: GeometryLinkingAlignmentLockParameters) => void;
    onLinkingStart: (startParams: GeometryPreliminaryLinkingParameters) => void;
    onStopLinking: () => void;
    resolution: number;
    layoutContext: LayoutContext;
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
};

const isNotPreliminary = (state: LinkingPhase) => state !== 'preliminary';

const GeometryAlignmentLinkingInfobox: React.FC<GeometryAlignmentLinkingInfoboxProps> = ({
    onSelect,
    onUnselect,
    geometryAlignment,
    selectedLayoutLocationTrack,
    selectedLayoutReferenceLine,
    planId,
    changeTimes,
    linkingState,
    onLinkingStart,
    onLockAlignment,
    onStopLinking,
    resolution,
    layoutContext,
    contentVisible,
    onContentVisibilityChange,
}) => {
    const { t } = useTranslation();
    const [showAddLocationTrackDialog, setShowAddLocationTrackDialog] = React.useState(false);
    const [showAddTrackNumberDialog, setShowAddTrackNumberDialog] = React.useState(false);
    const [linkingAlignmentType, setLinkingAlignmentType] = React.useState<MapAlignmentType>(
        MapAlignmentType.LocationTrack,
    );

    const linkingInProgress = linkingState?.state === 'setup' || linkingState?.state === 'allSet';
    const [linkingCallInProgress, setLinkingCallInProgress] = React.useState(false);

    const planStatus = useLoader(
        () => (planId ? getPlanLinkStatus(planId, layoutContext) : undefined),
        [
            planId,
            changeTimes.layoutLocationTrack,
            changeTimes.layoutReferenceLine,
            layoutContext.publicationState,
            layoutContext.branch,
        ],
    );

    const linkedReferenceLines = useLoader(() => {
        if (!planStatus) return undefined;
        const referenceLineIds = planStatus.alignments
            .filter((linkStatus) => linkStatus.id === geometryAlignment.id)
            .flatMap((linkStatus) => linkStatus.linkedReferenceLineIds);
        const referenceLinePromises = referenceLineIds.map((referenceLineId) =>
            getReferenceLine(referenceLineId, layoutContext),
        );
        return Promise.all(referenceLinePromises).then((lines) => lines.filter(filterNotEmpty));
    }, [planStatus, geometryAlignment]);

    const linkedLocationTracks = useLoader(() => {
        if (!planStatus) return undefined;
        const locationTrackIds = planStatus.alignments
            .filter((linkStatus) => linkStatus.id === geometryAlignment.id)
            .flatMap((linkStatus) => linkStatus.linkedLocationTrackIds);
        return getLocationTracks(locationTrackIds, layoutContext);
    }, [planStatus, geometryAlignment]);

    const [selectedLocationTrackInfoboxExtras, _] = useLocationTrackInfoboxExtras(
        selectedLayoutLocationTrack?.id,
        layoutContext,
        changeTimes,
    );

    const canLink =
        !linkingCallInProgress &&
        linkingState?.state === 'allSet' &&
        !selectedLocationTrackInfoboxExtras?.partOfUnfinishedSplit;

    const canLockAlignment =
        (linkingAlignmentType === 'REFERENCE_LINE' && selectedLayoutReferenceLine) ||
        (linkingAlignmentType === 'LOCATION_TRACK' &&
            selectedLayoutLocationTrack &&
            !selectedLocationTrackInfoboxExtras?.partOfUnfinishedSplit);

    const [linkedAlignmentIds, linkedAlignmentIdsStatus] = useLoaderWithStatus(
        () => getLinkedAlignmentIdsInPlan(planId, layoutContext),
        [
            planId,
            layoutContext.publicationState,
            layoutContext.branch,
            changeTimes.layoutLocationTrack,
            changeTimes.layoutReferenceLine,
        ],
    );
    const isLinked = linkedAlignmentIds ? linkedAlignmentIds.includes(geometryAlignment.id) : false;

    const handleTrackNumberSave = refreshTrackNumberSelection(
        draftLayoutContext(layoutContext),
        onSelect,
        onUnselect,
    );
    const handleLocationTrackSave = refreshLocationTrackSelection(
        draftLayoutContext(layoutContext),
        onSelect,
        onUnselect,
    );

    function linkingTypeBySegmentCount(
        alignment: LayoutLocationTrack | LayoutReferenceLine,
    ): LinkingType.LinkingGeometryWithAlignment | LinkingType.LinkingGeometryWithEmptyAlignment {
        return alignment.segmentCount > 0
            ? LinkingType.LinkingGeometryWithAlignment
            : LinkingType.LinkingGeometryWithEmptyAlignment;
    }

    function lockAlignment() {
        if (linkingAlignmentType === 'LOCATION_TRACK' && selectedLayoutLocationTrack) {
            onLockAlignment({
                alignment: {
                    id: selectedLayoutLocationTrack.id,
                    type: MapAlignmentType.LocationTrack,
                },
                type: linkingTypeBySegmentCount(selectedLayoutLocationTrack),
            });
        } else if (linkingAlignmentType === 'REFERENCE_LINE' && selectedLayoutReferenceLine) {
            onLockAlignment({
                alignment: {
                    id: selectedLayoutReferenceLine.id,
                    type: MapAlignmentType.ReferenceLine,
                },
                type: linkingTypeBySegmentCount(selectedLayoutReferenceLine),
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

                await (linkingState.layoutAlignment.type === 'LOCATION_TRACK'
                    ? linkGeometryWithLocationTrack(layoutContext.branch, linkingParameters)
                    : linkGeometryWithReferenceLine(layoutContext.branch, linkingParameters));

                Snackbar.success(
                    'tool-panel.alignment.geometry.linking-succeeded-and-previous-unlinked',
                );

                onStopLinking();
            } else if (linkingState?.type === LinkingType.LinkingGeometryWithEmptyAlignment) {
                const linkingParameters =
                    createLinkingGeometryWithEmptyAlignmentParameters(linkingState);
                await (linkingState.layoutAlignment.type === 'LOCATION_TRACK'
                    ? linkGeometryWithEmptyLocationTrack(layoutContext.branch, linkingParameters)
                    : linkGeometryWithEmptyReferenceLine(layoutContext.branch, linkingParameters));

                Snackbar.success('tool-panel.alignment.geometry.linking-succeeded');
                onStopLinking();
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
                        qaId="geometry-alignment-linked"
                        label={t('tool-panel.alignment.geometry.is-linked')}
                        className={styles['geometry-alignment-infobox__linked-status']}
                        value={
                            linkedAlignmentIdsStatus === LoaderStatus.Ready ? (
                                <LinkingStatusLabel isLinked={isLinked} />
                            ) : (
                                <Spinner />
                            )
                        }
                    />

                    {linkedLocationTracks && linkedLocationTracks.length > 0 && (
                        <LocationTrackNames linkedLocationTracks={linkedLocationTracks} />
                    )}
                    {linkedReferenceLines && linkedReferenceLines.length > 0 && (
                        <ReferenceLineNames
                            linkedReferenceLines={linkedReferenceLines}
                            layoutContext={layoutContext}
                            changeTimes={changeTimes}
                        />
                    )}

                    {linkingState && (
                        <React.Fragment>
                            <InfoboxField label={t('tool-panel.alignment.geometry.link-command')}>
                                <div
                                    className={styles['geometry-alignment-infobox__radio-buttons']}>
                                    <Radio
                                        qaId={'location-track-linking'}
                                        disabled={isNotPreliminary(linkingState.state)}
                                        checked={
                                            linkingAlignmentType === MapAlignmentType.LocationTrack
                                        }
                                        onChange={() =>
                                            setLinkingAlignmentType(MapAlignmentType.LocationTrack)
                                        }>
                                        {t('tool-panel.alignment.geometry.location-track')}
                                    </Radio>
                                    <Radio
                                        qaId={'reference-line-linking'}
                                        disabled={isNotPreliminary(linkingState.state)}
                                        checked={
                                            linkingAlignmentType === MapAlignmentType.ReferenceLine
                                        }
                                        onChange={() =>
                                            setLinkingAlignmentType(MapAlignmentType.ReferenceLine)
                                        }>
                                        {t('tool-panel.alignment.geometry.reference-line')}
                                    </Radio>
                                </div>
                            </InfoboxField>
                            {linkingAlignmentType === 'REFERENCE_LINE' ? (
                                <GeometryAlignmentLinkingReferenceLineCandidates
                                    geometryAlignment={geometryAlignment}
                                    layoutContext={layoutContext}
                                    trackNumberChangeTime={changeTimes.layoutTrackNumber}
                                    onSelect={onSelect}
                                    selectedLayoutReferenceLine={selectedLayoutReferenceLine}
                                    disableAddButton={
                                        linkingState.type !== LinkingType.UnknownAlignment
                                    }
                                    onShowAddTrackNumberDialog={() =>
                                        setShowAddTrackNumberDialog(true)
                                    }
                                />
                            ) : (
                                <GeometryAlignmentLinkingLocationTrackCandidates
                                    geometryAlignment={geometryAlignment}
                                    layoutContext={layoutContext}
                                    locationTrackChangeTime={changeTimes.layoutLocationTrack}
                                    onSelect={onSelect}
                                    selectedLayoutLocationTrack={selectedLayoutLocationTrack}
                                    disableAddButton={
                                        linkingState.type !== LinkingType.UnknownAlignment
                                    }
                                    onShowAddLocationTrackDialog={() =>
                                        setShowAddLocationTrackDialog(true)
                                    }
                                    selectedPartOfUnfinishedSplit={
                                        selectedLocationTrackInfoboxExtras?.partOfUnfinishedSplit ||
                                        false
                                    }
                                />
                            )}
                        </React.Fragment>
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
                            {linkingState.issues.map((errorKey) => (
                                <InfoboxContentSpread key={errorKey}>
                                    <MessageBox>{t(errorKey)}</MessageBox>
                                </InfoboxContentSpread>
                            ))}
                        </React.Fragment>
                    )}

                    {linkingState === undefined && (
                        <PrivilegeRequired privilege={EDIT_LAYOUT}>
                            <InfoboxButtons>
                                <Button
                                    disabled={layoutContext.publicationState !== 'DRAFT'}
                                    title={
                                        layoutContext.publicationState === 'OFFICIAL'
                                            ? t(
                                                  'tool-panel.disabled.activity-disabled-in-official-mode',
                                              )
                                            : ''
                                    }
                                    size={ButtonSize.SMALL}
                                    onClick={startLinking}
                                    qa-id="start-alignment-linking">
                                    {t(
                                        `tool-panel.alignment.geometry.${
                                            isLinked ? 'add-linking' : 'start-setup'
                                        }`,
                                    )}
                                </Button>
                            </InfoboxButtons>
                        </PrivilegeRequired>
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
                                onClick={lockAlignment}
                                qa-id="lock-alignment">
                                {t('tool-panel.alignment.geometry.lock-alignment')}
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
                                qa-id="link-geometry-alignment"
                                disabled={!canLink}
                                onClick={link}>
                                {t('tool-panel.alignment.geometry.save-link')}
                            </Button>
                        </InfoboxButtons>
                    )}
                </InfoboxContent>
            </Infobox>

            {showAddLocationTrackDialog && (
                <LocationTrackEditDialogContainer
                    onClose={() => setShowAddLocationTrackDialog(false)}
                    onSave={handleLocationTrackSave}
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
