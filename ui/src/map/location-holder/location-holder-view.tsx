import * as React from 'react';
import styles from './location-holder-view.scss';
import { formatToTM35FINString } from 'utils/geography-utils';
import { Point } from 'model/geometry';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import { LayoutContext, TrackMeter as TrackMeterModel } from 'common/common-model';
import { useDebouncedState, useLoader } from 'utils/react-utils';
import { getAddress } from 'common/geocoding-api';
import TrackMeter from 'geoviite-design-lib/track-meter/track-meter';
import { getLocationTrack } from 'track-layout/layout-location-track-api';
import { getTrackNumberById } from 'track-layout/layout-track-number-api';
import { getTrackNumberReferenceLine } from 'track-layout/layout-reference-line-api';
import { first } from 'utils/array-utils';

type LocationHolderProps = {
    hoveredCoordinate: Point | undefined;
    trackNumbers: LayoutTrackNumberId[];
    locationTracks: LocationTrackId[];
    layoutContext: LayoutContext;
};

type HoverLocation = {
    coordinate: Point | undefined;
    alignmentName: string | undefined;
    address: TrackMeterModel | undefined;
};

async function getLocationTrackHoverLocation(
    locationTrackId: LocationTrackId,
    layoutContext: LayoutContext,
    coordinate: Point,
): Promise<HoverLocation> {
    return getLocationTrack(locationTrackId, layoutContext).then((track) =>
        getHoverLocation(track?.name, track?.trackNumberId, layoutContext, coordinate),
    );
}

async function getReferenceLineHoverLocation(
    trackNumberId: LayoutTrackNumberId,
    layoutContext: LayoutContext,
    coordinate: Point,
): Promise<HoverLocation> {
    return getTrackNumberReferenceLine(trackNumberId, layoutContext).then((line) => {
        if (line) {
            return getTrackNumberById(line.trackNumberId, layoutContext).then((trackNumber) =>
                getHoverLocation(trackNumber?.number, trackNumber?.id, layoutContext, coordinate),
            );
        } else {
            return emptyHoveredLocation(coordinate);
        }
    });
}

async function getHoverLocation(
    name: string | undefined,
    trackNumberId: LayoutTrackNumberId | undefined,
    layoutContext: LayoutContext,
    coordinate: Point,
): Promise<HoverLocation> {
    if (trackNumberId) {
        return getAddress(trackNumberId, coordinate, layoutContext).then((address) => ({
            alignmentName: name,
            coordinate: coordinate,
            address: address || undefined,
        }));
    } else {
        return Promise.resolve({
            alignmentName: name,
            coordinate: coordinate,
            address: undefined,
        });
    }
}

function emptyHoveredLocation(coordinate: Point | undefined): HoverLocation {
    return {
        alignmentName: undefined,
        coordinate: coordinate,
        address: undefined,
    };
}

export const LocationHolderView: React.FC<LocationHolderProps> = ({
    hoveredCoordinate,
    trackNumbers,
    locationTracks,
    layoutContext,
}: LocationHolderProps) => {
    const debouncedCoordinate = useDebouncedState(hoveredCoordinate, 100);
    const trackNumber = first(trackNumbers);
    const locationTrack = first(locationTracks);

    const hovered = useLoader(() => {
        if (trackNumber && hoveredCoordinate) {
            return getReferenceLineHoverLocation(trackNumber, layoutContext, hoveredCoordinate);
        } else if (locationTrack && hoveredCoordinate) {
            return getLocationTrackHoverLocation(locationTrack, layoutContext, hoveredCoordinate);
        } else {
            return Promise.resolve(emptyHoveredLocation(hoveredCoordinate));
        }
    }, [debouncedCoordinate, trackNumber, locationTrack]);

    return (
        <div className={styles['location-holder-view']}>
            <div>{hovered?.alignmentName}</div>
            <div>
                <TrackMeter trackMeter={hovered?.address} />
            </div>
            <div>{hovered?.coordinate ? formatToTM35FINString(hovered.coordinate) : ''}</div>
        </div>
    );
};
