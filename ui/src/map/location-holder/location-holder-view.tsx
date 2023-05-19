import * as React from 'react';
import styles from './location-holder-view.scss';
import { formatToTM35FINString } from 'utils/geography-utils';
import { appStore } from 'store/store';
import { Point } from 'model/geometry';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import { PublishType, TrackMeter as TrackMeterModel } from 'common/common-model';
import { useDebouncedState, useLoader } from 'utils/react-utils';
import { getAddress } from 'common/geocoding-api';
import TrackMeter from 'geoviite-design-lib/track-meter/track-meter';
import { getLocationTrack } from 'track-layout/layout-location-track-api';
import { getTrackNumberById } from 'track-layout/layout-track-number-api';
import { getTrackNumberReferenceLine } from 'track-layout/layout-reference-line-api';

type LocationHolderProps = {
    hoveredCoordinate: Point | null;
};

type HoverLocation = {
    coordinate: Point | null;
    alignmentName: string | null;
    address: TrackMeterModel | null;
};

export async function getLocationTrackHoverLocation(
    locationTrackId: LocationTrackId,
    publishType: PublishType,
    coordinate: Point,
): Promise<HoverLocation> {
    return getLocationTrack(locationTrackId, publishType).then((track) =>
        getHoverLocation(track?.name, track?.trackNumberId, publishType, coordinate),
    );
}

export async function getReferenceLineHoverLocation(
    trackNumberId: LayoutTrackNumberId,
    publishType: PublishType,
    coordinate: Point,
): Promise<HoverLocation> {
    return getTrackNumberReferenceLine(trackNumberId, publishType).then((line) => {
        if (line) {
            return getTrackNumberById(line.trackNumberId, publishType).then((trackNumber) =>
                getHoverLocation(trackNumber?.number, trackNumber?.id, publishType, coordinate),
            );
        } else {
            return emptyHoveredLocation(coordinate);
        }
    });
}

export async function getHoverLocation(
    name: string | null | undefined,
    trackNumberId: LayoutTrackNumberId | null | undefined,
    publishType: PublishType,
    coordinate: Point,
): Promise<HoverLocation> {
    if (trackNumberId) {
        return getAddress(trackNumberId, coordinate, publishType).then((address) => ({
            alignmentName: name || '',
            coordinate: coordinate,
            address: address || null,
        }));
    } else {
        return Promise.resolve({
            alignmentName: name || '',
            coordinate: coordinate,
            address: null,
        });
    }
}

async function getHoverLocationBySelection(coordinate: Point | null): Promise<HoverLocation> {
    const trackNumberIds = appStore.getState().trackLayout.selection.selectedItems.trackNumbers;
    const locationTracks = appStore.getState().trackLayout.selection.selectedItems.locationTracks;
    const publishType = appStore.getState().trackLayout.publishType;
    if (trackNumberIds.length == 1 && coordinate != null) {
        return getReferenceLineHoverLocation(trackNumberIds[0], publishType, coordinate);
    } else if (locationTracks.length == 1 && coordinate != null) {
        return getLocationTrackHoverLocation(locationTracks[0], publishType, coordinate);
    } else {
        return Promise.resolve(emptyHoveredLocation(coordinate));
    }
}

function emptyHoveredLocation(coordinate: Point | null): HoverLocation {
    return {
        alignmentName: null,
        coordinate: coordinate,
        address: null,
    };
}

export const LocationHolderView: React.FC<LocationHolderProps> = ({
    hoveredCoordinate,
}: LocationHolderProps) => {
    const debouncedCoordinate = useDebouncedState(hoveredCoordinate, 100);

    const hovered = useLoader(
        () => getHoverLocationBySelection(debouncedCoordinate || null),
        [debouncedCoordinate],
    );

    return (
        <div className={styles['location-holder-view']}>
            <div>{hovered?.alignmentName}</div>
            <div>{hovered?.address ? <TrackMeter value={hovered.address} /> : ''}</div>
            <div>{hovered?.coordinate ? formatToTM35FINString(hovered.coordinate) : ''}</div>
        </div>
    );
};
