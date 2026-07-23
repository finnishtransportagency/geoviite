import {
    BoundaryMoveDisabledReason,
    LayoutLocationTrack,
    LocationTrackId,
    SwitchJointId,
} from 'track-layout/track-layout-model';
import { ChangingTrackBoundary } from 'linking/linking-model';
import { LayoutContext } from 'common/common-model';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { useCommonDataAppSelector } from 'store/hooks';
import { getLocationTrack, getLocationTracks } from 'track-layout/layout-location-track-api';
import { getAddress } from 'common/geocoding-api';
import { formatTrackMeter } from 'utils/geography-utils';
import { EMPTY_ARRAY } from 'utils/array-utils';
import { ChangeTimes } from 'common/common-slice';
import React from 'react';
import { useTranslation } from 'react-i18next';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { LocationTrackCandidates } from 'linking/alignment-linking-candidates';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { MessageBox, MessageBoxType } from 'geoviite-design-lib/message-box/message-box';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import {
    LocationTrackBadge,
    LocationTrackBadgeStatus,
} from 'geoviite-design-lib/alignment/location-track-badge';
import {
    BoundaryMoveCounterpart,
    BoundaryMoveDirection,
    getTrackBoundaryMoveCounterpartOptions,
    saveTrackBoundaryMove,
    TrackBoundaryMoveRequest,
} from 'track-layout/track-boundary-move-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { updateLocationTrackChangeTime } from 'common/change-time-api';
import { TFunction } from 'i18next';

type LocationTrackBoundaryMoveInfoboxContainerProps = {
    locationTrack: LayoutLocationTrack;
    linkingState: ChangingTrackBoundary;
    layoutContext: LayoutContext;
};

export const LocationTrackBoundaryMoveInfoboxContainer: React.FC<
    LocationTrackBoundaryMoveInfoboxContainerProps
> = ({ locationTrack, linkingState, layoutContext }) => {
    const { t } = useTranslation();
    const delegates = createDelegates(TrackLayoutActions);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    return (
        <LocationTrackBoundaryMoveInfobox
            locationTrack={locationTrack}
            linkingState={linkingState}
            layoutContext={layoutContext}
            onSelectCounterpart={delegates.setTrackBoundaryMoveCounterpart}
            changeTimes={changeTimes}
            onLoadCounterpartOptions={delegates.setTrackBoundaryMoveCounterpartOptions}
            onStopTrackBoundaryMove={() => {
                delegates.removeForcedVisibleLayer(['alignment-linking-layer']);
                delegates.stopLinking();
            }}
            onConfirmCounterpartSelection={() =>
                delegates.confirmTrackBoundaryMoveCounterpartSelection()
            }
            onSaveTrackBoundaryMove={async (request) => {
                try {
                    await saveTrackBoundaryMove(layoutContext, request);
                } catch {
                    Snackbar.error(
                        t('tool-panel.location-track.track-boundary-move.boundary-move-failed'),
                    );
                    return;
                }
                await updateLocationTrackChangeTime();

                const counterpart = linkingState.counterpart;
                const selectedTarget = linkingState.selectedTarget;
                const counterpartTrack =
                    counterpart === undefined
                        ? undefined
                        : await getLocationTrack(counterpart.trackId, layoutContext);
                const address =
                    selectedTarget === undefined
                        ? undefined
                        : await getAddress(
                              locationTrack.trackNumberId,
                              selectedTarget.location,
                              layoutContext,
                          );
                Snackbar.success(
                    t('tool-panel.location-track.track-boundary-move.boundary-move-saved', {
                        firstTrack: locationTrack.name,
                        secondTrack: counterpartTrack?.name ?? '',
                        address: address === undefined ? '' : formatTrackMeter(address),
                    }),
                );
                delegates.removeForcedVisibleLayer(['alignment-linking-layer']);
                delegates.stopLinking();
            }}
        />
    );
};

type LocationTrackBoundaryMoveInfoboxProps = {
    locationTrack: LayoutLocationTrack;
    linkingState: ChangingTrackBoundary;
    layoutContext: LayoutContext;
    changeTimes: ChangeTimes;
    onLoadCounterpartOptions: (counterpartOptions: BoundaryMoveCounterpart[]) => void;
    onStopTrackBoundaryMove: () => void;
    onSelectCounterpart: (counterpart: BoundaryMoveCounterpart) => void;
    onConfirmCounterpartSelection: () => void;
    onSaveTrackBoundaryMove: (request: TrackBoundaryMoveRequest) => Promise<void>;
};

function switchJointIdEquals(a: SwitchJointId, b: SwitchJointId): boolean {
    return a.switchId === b.switchId && a.jointNumber === b.jointNumber;
}

const boundaryMoveDisabledReasonTranslationKeys: Record<BoundaryMoveDisabledReason, string> = {
    PART_OF_SPLIT: 'tool-panel.location-track.track-boundary-move.validation.part-of-split',
    PART_OF_BOUNDARY_MOVE:
        'tool-panel.location-track.track-boundary-move.validation.part-of-boundary-move',
    TRACK_DRAFT_EXISTS:
        'tool-panel.location-track.track-boundary-move.validation.track-draft-exists',
    NO_GEOMETRY: 'tool-panel.location-track.no-geometry',
    SWITCHES_PART_OF_SPLIT:
        'tool-panel.location-track.track-boundary-move.validation.switches-part-of-split',
    ON_DIFFERENT_TRACK_NUMBER:
        'tool-panel.location-track.track-boundary-move.validation.on-different-track-number',
    OVERLAPPING_ADDRESSES:
        'tool-panel.location-track.track-boundary-move.validation.overlapping-addresses',
    GEOCODING_FAILED: 'tool-panel.location-track.track-boundary-move.validation.geocoding-failed',
};

export const translatedBoundaryMoveDisabledReasons = (
    reasons: BoundaryMoveDisabledReason[],
    t: TFunction<'translation', undefined>,
): string | undefined =>
    reasons.length === 0
        ? undefined
        : reasons
              .map((reason) => t(boundaryMoveDisabledReasonTranslationKeys[reason]))
              .join('\n\n');

function canSave(linkingState: ChangingTrackBoundary): boolean {
    const selectedTarget = linkingState.selectedTarget;
    const counterpart = linkingState.counterpart;
    return (
        selectedTarget !== undefined &&
        counterpart !== undefined &&
        (selectedTarget.kind === 'joint'
            ? counterpart.connectingSwitchJoint === undefined ||
              !switchJointIdEquals(selectedTarget.joint, counterpart.connectingSwitchJoint)
            : true)
    );
}

const LocationTrackBoundaryMoveInfobox: React.FC<LocationTrackBoundaryMoveInfoboxProps> = ({
    locationTrack,
    linkingState,
    layoutContext,
    changeTimes,
    onLoadCounterpartOptions,
    onSelectCounterpart,
    onConfirmCounterpartSelection,
    onStopTrackBoundaryMove,
    onSaveTrackBoundaryMove,
}) => {
    const { t } = useTranslation();

    const [counterpartOptions, setCounterpartOptions] = React.useState<BoundaryMoveCounterpart[]>(
        [],
    );
    const [saving, setSaving] = React.useState(false);
    const [deleteShorteningTrack, setDeleteShorteningTrack] = React.useState(true);

    const [candidates, candidatesLoaderStatus] = useLoaderWithStatus(async () => {
        const options = await getTrackBoundaryMoveCounterpartOptions(
            layoutContext,
            locationTrack.id,
        );
        onLoadCounterpartOptions(options);
        setCounterpartOptions(options);
        const trackIds = options.map((o) => o.trackId);
        if (trackIds.length === 0) return [];
        return getLocationTracks(trackIds, layoutContext);
    }, [locationTrack.id, layoutContext, changeTimes.layoutLocationTrack]);

    const counterpart = linkingState.counterpart;
    const counterpartLocked = linkingState.counterpartLocked;

    const selectCounterpart = (selectedTrack: LocationTrackId) => {
        const picked = counterpartOptions.find((o) => o.trackId === selectedTrack);
        if (picked && picked.disabledReasons.length === 0) {
            onSelectCounterpart(picked);
        }
    };

    const counterpartDisabledReason = (track: LayoutLocationTrack): string | undefined =>
        translatedBoundaryMoveDisabledReasons(
            counterpartOptions.find((o) => o.trackId === track.id)?.disabledReasons ?? [],
            t,
        );

    const selectedTarget = linkingState.selectedTarget;
    const saveBoundaryMove = () => {
        if (counterpart === undefined || selectedTarget === undefined) {
            return;
        }
        const headShortens = selectedTarget.role === 'head';
        const headFirst = counterpart.orientation === 'HEAD_FIRST';
        const boundaryMoveDirection: BoundaryMoveDirection =
            headFirst === headShortens ? 'ASCENDING' : 'DESCENDING';
        setSaving(true);
        onSaveTrackBoundaryMove({
            shorteningTrackId: headShortens ? locationTrack.id : counterpart.trackId,
            lengtheningTrackId: headShortens ? counterpart.trackId : locationTrack.id,
            upToSwitchJoint: selectedTarget.kind === 'joint' ? selectedTarget.joint : undefined,
            boundaryMoveDirection,
            deleteShorteningTrack: selectedTarget.kind === 'end' && deleteShorteningTrack,
        }).finally(() => setSaving(false));
    };

    const counterpartTrack = (candidates ?? []).find((c) => c.id === counterpart?.trackId);

    return (
        <Infobox
            title={
                counterpartLocked
                    ? t('tool-panel.location-track.track-boundary-move.title-post-select')
                    : t('tool-panel.location-track.track-boundary-move.title-pre-select')
            }
            contentVisible={true}>
            <InfoboxContent>
                <InfoboxField
                    label={t('tool-panel.location-track.track-boundary-move.first-track')}>
                    <LocationTrackBadge
                        locationTrack={locationTrack}
                        status={LocationTrackBadgeStatus.SELECTED}
                    />
                </InfoboxField>

                {counterpartLocked ? (
                    <React.Fragment>
                        <InfoboxField
                            label={t('tool-panel.location-track.track-boundary-move.second-track')}>
                            {counterpartTrack && (
                                <LocationTrackBadge
                                    locationTrack={counterpartTrack}
                                    status={LocationTrackBadgeStatus.SELECTED}
                                />
                            )}
                        </InfoboxField>

                        {linkingState.selectedTarget === undefined && (
                            <MessageBox type={MessageBoxType.INFO}>
                                {t(
                                    'tool-panel.location-track.track-boundary-move.select-boundary-on-map',
                                )}
                            </MessageBox>
                        )}

                        {linkingState.selectedTarget?.kind === 'end' && (
                            <div>
                                <Checkbox
                                    checked={deleteShorteningTrack}
                                    onChange={(e) => setDeleteShorteningTrack(e.target.checked)}>
                                    {t(
                                        'tool-panel.location-track.track-boundary-move.delete-shortening-track',
                                    )}
                                </Checkbox>
                            </div>
                        )}

                        <InfoboxButtons>
                            <Button
                                size={ButtonSize.SMALL}
                                variant={ButtonVariant.SECONDARY}
                                disabled={saving}
                                onClick={onStopTrackBoundaryMove}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                size={ButtonSize.SMALL}
                                disabled={saving || !canSave(linkingState)}
                                isProcessing={saving}
                                onClick={saveBoundaryMove}>
                                {t(
                                    'tool-panel.location-track.track-boundary-move.save-boundary-move',
                                )}
                            </Button>
                        </InfoboxButtons>
                    </React.Fragment>
                ) : (
                    <React.Fragment>
                        <InfoboxField
                            label={t(
                                'tool-panel.location-track.track-boundary-move.select-second-track',
                            )}
                        />
                        <LocationTrackCandidates
                            candidates={candidates || EMPTY_ARRAY}
                            selectedId={counterpart?.trackId}
                            isLoading={candidatesLoaderStatus === LoaderStatus.Loading}
                            emptyMessage={t(
                                'tool-panel.location-track.track-boundary-move.no-candidates',
                            )}
                            withTopBorder
                            getDisabledReason={counterpartDisabledReason}
                            onSelect={selectCounterpart}
                        />

                        <MessageBox type={MessageBoxType.INFO}>
                            {t('tool-panel.location-track.track-boundary-move.candidates-info')}
                        </MessageBox>

                        <InfoboxButtons>
                            <Button
                                size={ButtonSize.SMALL}
                                variant={ButtonVariant.SECONDARY}
                                onClick={onStopTrackBoundaryMove}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                size={ButtonSize.SMALL}
                                disabled={counterpart === undefined}
                                onClick={onConfirmCounterpartSelection}>
                                {t('tool-panel.location-track.track-boundary-move.lock-selection')}
                            </Button>
                        </InfoboxButtons>
                    </React.Fragment>
                )}
            </InfoboxContent>
        </Infobox>
    );
};
