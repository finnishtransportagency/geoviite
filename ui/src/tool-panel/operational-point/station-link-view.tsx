import React from 'react';
import {
    StationLink,
    OperationalPointId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { useOperationalPoint, useLocationTracks } from 'track-layout/track-layout-react-utils';
import { TrackNumberLink } from 'geoviite-design-lib/track-number/track-number-link';
import { LocationTrackBadge } from 'geoviite-design-lib/alignment/location-track-badge';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import styles from './operational-point-infobox.scss';

export type StationLinkViewProps = {
    stationLink: StationLink;
    ownOperationalPointId: OperationalPointId;
    layoutContext: LayoutContext;
    changeTimes: ChangeTimes;
    onSelectLocationTrack: (locationTrackId: LocationTrackId) => void;
};

export const StationLinkView: React.FC<StationLinkViewProps> = ({
    stationLink,
    ownOperationalPointId,
    layoutContext,
    changeTimes,
    onSelectLocationTrack,
}) => {
    const otherOpId =
        stationLink.startOperationalPointId === ownOperationalPointId
            ? stationLink.endOperationalPointId
            : stationLink.startOperationalPointId;

    const otherOp = useOperationalPoint(otherOpId, layoutContext, changeTimes.operationalPoints);

    const locationTracks = useLocationTracks(
        stationLink.locationTrackIds,
        layoutContext,
        changeTimes.layoutLocationTrack,
    );

    if (!otherOp) {
        return <Spinner />;
    }

    return (
        <>
            <div className={styles['operational-point-infobox__station-link-header']}>
                <span className={styles['operational-point-infobox__station-link-name']}>
                    {otherOp.name}
                </span>
                <TrackNumberLink
                    trackNumberId={stationLink.trackNumberId}
                    layoutContext={layoutContext}
                    changeTime={changeTimes.layoutTrackNumber}
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
                            onClick={() => onSelectLocationTrack(track.id)}
                        />
                    ))}
                </div>
            )}
        </>
    );
};
