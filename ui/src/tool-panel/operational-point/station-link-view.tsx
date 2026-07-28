import React from 'react';
import { OperationalPointId, StationLink } from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { useLocationTracks } from 'track-layout/track-layout-react-utils';
import {
    LocationTrackBadge,
    LocationTrackBadgeStatus,
} from 'geoviite-design-lib/alignment/location-track-badge';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { OperationalPointBadgeLink } from 'geoviite-design-lib/operational-point/operational-point-badge';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import styles from './operational-point-infobox.scss';
import {
    TrackNumberBadgeLink,
    TrackNumberBadgeStatus,
} from 'geoviite-design-lib/alignment/track-number-badge';

export type StationLinkViewProps = {
    stationLink: StationLink;
    ownOperationalPointId?: OperationalPointId;
    layoutContext: LayoutContext;
    changeTimes: ChangeTimes;
    isLinkingOrSplitting: boolean;
};

export const StationLinkView: React.FC<StationLinkViewProps> = ({
    stationLink,
    ownOperationalPointId,
    layoutContext,
    changeTimes,
    isLinkingOrSplitting,
}) => {
    const locationTracks = useLocationTracks(
        stationLink.locationTrackIds,
        layoutContext,
        changeTimes.layoutLocationTrack,
    );

    const [firstOp, secondOp] =
        ownOperationalPointId && stationLink.endOperationalPointId === ownOperationalPointId
            ? [stationLink.endOperationalPointId, stationLink.startOperationalPointId]
            : [stationLink.startOperationalPointId, stationLink.endOperationalPointId];

    return (
        <>
            <div className={styles['operational-point-infobox__station-link-header']}>
                <span className={styles['operational-point-infobox__station-link-name']}>
                    <OperationalPointBadgeLink
                        operationalPointId={firstOp}
                        layoutContext={layoutContext}
                        changeTime={changeTimes.operationalPoints}
                        disabled={isLinkingOrSplitting}
                    />
                    <Icons.Next size={IconSize.SMALL} color={IconColor.INHERIT} />
                    <OperationalPointBadgeLink
                        operationalPointId={secondOp}
                        layoutContext={layoutContext}
                        changeTime={changeTimes.operationalPoints}
                        disabled={isLinkingOrSplitting}
                    />
                </span>
                <TrackNumberBadgeLink
                    trackNumberId={stationLink.trackNumberId}
                    layoutContext={layoutContext}
                    changeTime={changeTimes.layoutTrackNumber}
                    status={isLinkingOrSplitting ? TrackNumberBadgeStatus.DISABLED : undefined}
                />
                <span className={styles['operational-point-infobox__station-link-length']}>
                    {Math.round(stationLink.length)} m
                </span>
            </div>
            {locationTracks.length > 0 && (
                <div className={styles['operational-point-infobox__station-link-tracks']}>
                    {locationTracks.map((track) => (
                        <LocationTrackBadge
                            key={track.id}
                            locationTrack={track}
                            status={
                                isLinkingOrSplitting ? LocationTrackBadgeStatus.DISABLED : undefined
                            }
                            onClick={
                                isLinkingOrSplitting
                                    ? undefined
                                    : () => {
                                          const delegates = createDelegates(TrackLayoutActions);
                                          delegates.onSelect({
                                              locationTracks: [track.id],
                                              selectedTab: {
                                                  id: track.id,
                                                  type: 'LOCATION_TRACK',
                                              },
                                          });
                                      }
                            }
                        />
                    ))}
                </div>
            )}
        </>
    );
};
