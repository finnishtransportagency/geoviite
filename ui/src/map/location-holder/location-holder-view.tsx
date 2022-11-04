import * as React from 'react';
import styles from './location-holder-view.scss';
import { formatToTM35FINString } from 'utils/geography-utils';
import { trackLayoutStore } from 'store/store';
import { Point } from 'model/geometry';
import {
    getLocationTrack,
    getReferenceLine,
    getTrackNumberById,
} from 'track-layout/track-layout-api';
import {
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { PublishType, TrackMeter as TrackMeterModel } from 'common/common-model';
import { useDebouncedState, useLoader } from 'utils/react-utils';
import { getAddress } from 'common/geocoding-api';
import TrackMeter from 'geoviite-design-lib/track-meter/track-meter';

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
) {
    return getLocationTrack(locationTrackId, publishType).then((track) =>
        getHoverLocation(track.name, track.trackNumberId, publishType, coordinate),
    );
}

export async function getReferenceLineHoverLocation(
    referenceLineId: ReferenceLineId,
    publishType: PublishType,
    coordinate: Point,
) {
    return getReferenceLine(referenceLineId, publishType).then((line) =>
        getTrackNumberById(line.trackNumberId, publishType).then((trackNumber) =>
            getHoverLocation(trackNumber?.number, trackNumber?.id, publishType, coordinate),
        ),
    );
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
    const referenceLines =
        trackLayoutStore.getState().trackLayout.selection.selectedItems.referenceLines;
    const locationTracks =
        trackLayoutStore.getState().trackLayout.selection.selectedItems.locationTracks;
    const publishType = trackLayoutStore.getState().trackLayout.publishType;
    if (referenceLines.length == 1 && coordinate != null) {
        return getReferenceLineHoverLocation(referenceLines[0], publishType, coordinate);
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
