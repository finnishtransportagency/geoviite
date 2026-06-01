import { LayoutLocationTrack, LocationTrackId } from 'track-layout/track-layout-model';
import { ChangingTrackBoundary } from 'linking/linking-model';
import { LayoutContext } from 'common/common-model';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { useCommonDataAppSelector } from 'store/hooks';
import { getLocationTracks } from 'track-layout/layout-location-track-api';
import { EMPTY_ARRAY } from 'utils/array-utils';
import { ChangeTimes } from 'common/common-slice';
import React from 'react';
import { useTranslation } from 'react-i18next';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { LocationTrackCandidates } from 'linking/alignment-linking-candidates';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { MessageBox, MessageBoxType } from 'geoviite-design-lib/message-box/message-box';
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

type LocationTrackBoundaryMoveInfoboxContainerProps = {
    locationTrack: LayoutLocationTrack;
    linkingState: ChangingTrackBoundary;
    layoutContext: LayoutContext;
};

export const LocationTrackBoundaryMoveInfoboxContainer: React.FC<
    LocationTrackBoundaryMoveInfoboxContainerProps
> = ({ locationTrack, linkingState, layoutContext }) => {
    const delegates = createDelegates(TrackLayoutActions);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    return (
        <LocationTrackBoundaryMoveInfobox
            locationTrack={locationTrack}
            linkingState={linkingState}
            layoutContext={layoutContext}
            onSelectCounterpart={delegates.setTrackBoundaryMoveCounterpart}
            changeTimes={changeTimes}
            onStopTrackBoundaryMove={() => {
                delegates.removeForcedVisibleLayer(['alignment-linking-layer']);
                delegates.stopLinking();
            }}
            onSaveTrackBoundaryMove={async (request) => {
                await saveTrackBoundaryMove(layoutContext, request);
                await updateLocationTrackChangeTime();
                Snackbar.success(
                    'tool-panel.location-track.track-boundary-move.boundary-move-saved',
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
    onStopTrackBoundaryMove: () => void;
    onSelectCounterpart: (counterpart: BoundaryMoveCounterpart) => void;
    onSaveTrackBoundaryMove: (request: TrackBoundaryMoveRequest) => Promise<void>;
};

const LocationTrackBoundaryMoveInfobox: React.FC<LocationTrackBoundaryMoveInfoboxProps> = ({
    locationTrack,
    linkingState,
    layoutContext,
    changeTimes,
    onSelectCounterpart,
    onStopTrackBoundaryMove,
    onSaveTrackBoundaryMove,
}) => {
    const { t } = useTranslation();

    const [counterpartOptions, setCounterpartOptions] = React.useState<BoundaryMoveCounterpart[]>(
        [],
    );
    const [pickedTrackId, setPickedTrackId] = React.useState<LocationTrackId>();
    const [saving, setSaving] = React.useState(false);

    const [candidates, candidatesLoaderStatus] = useLoaderWithStatus(async () => {
        const options = await getTrackBoundaryMoveCounterpartOptions(
            layoutContext,
            locationTrack.id,
        );
        setCounterpartOptions(options);
        const trackIds = options.map((o) => o.trackId);
        if (trackIds.length === 0) return [];
        return getLocationTracks(trackIds, layoutContext);
    }, [locationTrack.id, layoutContext, changeTimes.layoutLocationTrack]);

    const counterpart = linkingState.counterpart;
    const counterpartSelected = counterpart !== undefined;

    const lockSelection = () => {
        const picked = counterpartOptions.find((o) => o.trackId === pickedTrackId);
        if (picked) {
            onSelectCounterpart(picked);
        }
    };

    const selectedJoint = linkingState.selectedJoint;
    const saveBoundaryMove = () => {
        if (counterpart === undefined || selectedJoint === undefined) {
            return;
        }
        const headShortens = selectedJoint.role === 'head';
        const headFirst = counterpart.orientation === 'HEAD_FIRST';
        const boundaryMoveDirection: BoundaryMoveDirection =
            headFirst === headShortens ? 'ASCENDING' : 'DESCENDING';
        setSaving(true);
        onSaveTrackBoundaryMove({
            shorteningTrackId: headShortens ? locationTrack.id : counterpart.trackId,
            lengtheningTrackId: headShortens ? counterpart.trackId : locationTrack.id,
            switch: selectedJoint.joint.switchId,
            switchJoint: selectedJoint.joint.jointNumber,
            boundaryMoveDirection,
        }).finally(() => setSaving(false));
    };

    const counterpartTrack = (candidates ?? []).find((c) => c.id === counterpart?.trackId);

    return (
        <Infobox
            title={
                counterpartSelected
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

                {counterpartSelected ? (
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

                        {linkingState.selectedJoint === undefined && (
                            <MessageBox type={MessageBoxType.INFO}>
                                {t(
                                    'tool-panel.location-track.track-boundary-move.select-boundary-on-map',
                                )}
                            </MessageBox>
                        )}

                        <InfoboxButtons>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                disabled={saving}
                                onClick={onStopTrackBoundaryMove}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                disabled={selectedJoint === undefined || saving}
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
                            selectedId={pickedTrackId}
                            isLoading={candidatesLoaderStatus === LoaderStatus.Loading}
                            emptyMessage={t(
                                'tool-panel.location-track.track-boundary-move.no-candidates',
                            )}
                            onSelect={setPickedTrackId}
                        />

                        <MessageBox type={MessageBoxType.INFO}>
                            {t('tool-panel.location-track.track-boundary-move.candidates-info')}
                        </MessageBox>

                        <InfoboxButtons>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                onClick={onStopTrackBoundaryMove}>
                                {t('button.cancel')}
                            </Button>
                            <Button disabled={pickedTrackId === undefined} onClick={lockSelection}>
                                {t('tool-panel.location-track.track-boundary-move.lock-selection')}
                            </Button>
                        </InfoboxButtons>
                    </React.Fragment>
                )}
            </InfoboxContent>
        </Infobox>
    );
};
