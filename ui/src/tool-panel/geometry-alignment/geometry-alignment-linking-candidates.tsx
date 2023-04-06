import * as React from 'react';
import styles from 'tool-panel/geometry-alignment/geometry-alignment-infobox.scss';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import {
    ReferenceLineBadge,
    ReferenceLineBadgeStatus,
} from 'geoviite-design-lib/alignment/reference-line-badge';
import {
    LocationTrackBadge,
    LocationTrackBadgeStatus,
} from 'geoviite-design-lib/alignment/location-track-badge';
import LocationTrackTypeLabel from 'geoviite-design-lib/alignment/location-track-type-label';
import { useTranslation } from 'react-i18next';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import {
    LayoutLocationTrack,
    LayoutReferenceLine,
    LocationTrackId,
    MapAlignment,
    MapAlignmentType,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import {
    getNonLinkedReferenceLines,
    getReferenceLinesNear,
} from 'track-layout/layout-reference-line-api';
import { deduplicateById, fieldComparator, negComparator } from 'utils/array-utils';
import { expandBoundingBox } from 'model/geometry';
import {
    getLocationTracksNear,
    getNonLinkedLocationTracks,
} from 'track-layout/layout-location-track-api';
import { PublishType, TimeStamp } from 'common/common-model';
import {
    LinkingGeometryWithAlignment,
    LinkingGeometryWithEmptyAlignment,
    PreliminaryLinkingGeometry,
} from 'linking/linking-model';
import { OnSelectOptions } from 'selection/selection-model';

type GeometryAlignmentLinkingCandidatesProps = {
    geometryAlignment: MapAlignment;
    publishType: PublishType;
    trackNumberChangeTime: TimeStamp;
    locationTrackChangeTime: TimeStamp;
    selectedLayoutLocationTrack?: LayoutLocationTrack;
    selectedLayoutReferenceLine?: LayoutReferenceLine;
    linkingState?:
        | LinkingGeometryWithAlignment
        | LinkingGeometryWithEmptyAlignment
        | PreliminaryLinkingGeometry;
    onSelect: (options: OnSelectOptions) => void;
    onShowAddTrackNumberDialog: () => void;
    onShowAddLocationTrackDialog: () => void;
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

const NEAR_TRACK_SEARCH_BUFFER = 10.0;

export const GeometryAlignmentLinkingCandidates: React.FC<
    GeometryAlignmentLinkingCandidatesProps
> = ({
    geometryAlignment,
    publishType,
    trackNumberChangeTime,
    locationTrackChangeTime,
    selectedLayoutLocationTrack,
    selectedLayoutReferenceLine,
    linkingState,
    onSelect,
    onShowAddTrackNumberDialog,
    onShowAddLocationTrackDialog,
}) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbers(publishType, trackNumberChangeTime);
    const [alignmentRefs, setAlignmentRefs] = React.useState<AlignmentRefs>({});
    const [locationTracks, setLocationTracks] = React.useState<LayoutLocationTrack[]>([]);
    const [referenceLines, setReferenceLines] = React.useState<LayoutReferenceLine[]>([]);

    const linkingInProgress = linkingState?.state === 'setup' || linkingState?.state === 'allSet';

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
    }, [geometryAlignment.boundingBox, locationTrackChangeTime, trackNumberChangeTime]);

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
    }, [geometryAlignment.boundingBox, locationTrackChangeTime]);

    React.useEffect(() => {
        const tracks = locationTracks.map((lt) => createLocationTrackSelection(lt));
        if (
            selectedLayoutLocationTrack &&
            !locationTracks.some((lt) => lt.id === selectedLayoutLocationTrack.id)
        ) {
            tracks.push(createLocationTrackSelection(selectedLayoutLocationTrack));
        }
        const lines = referenceLines.map((rl) => createReferenceLineSelection(rl));
        if (
            selectedLayoutReferenceLine &&
            !referenceLines.some((lt) => lt.id === selectedLayoutReferenceLine.id)
        ) {
            tracks.push(createReferenceLineSelection(selectedLayoutReferenceLine));
        }
        const newRefs = [...tracks, ...lines].reduce((acc: AlignmentRefs, value) => {
            acc[value.key] = value;
            return acc;
        }, {});
        setAlignmentRefs(newRefs);
    }, [selectedLayoutLocationTrack, selectedLayoutReferenceLine, locationTracks, referenceLines]);

    React.useEffect(() => {
        const selectedRef = getKey(selectedLayoutReferenceLine, selectedLayoutLocationTrack);
        if (selectedRef && alignmentRefs[selectedRef]) {
            alignmentRefs[selectedRef].ref.current?.scrollIntoView({
                behavior: 'smooth',
                block: 'nearest',
            });
        }
    }, [selectedLayoutReferenceLine, selectedLayoutLocationTrack]);

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

    return (
        <div className={styles['geometry-alignment-infobox__linking-container']}>
            <div className={styles['geometry-alignment-infobox__search-container']}>
                <InfoboxText value={t('tool-panel.alignment.geometry.choose-reference-line')} />
                <Button
                    variant={ButtonVariant.GHOST}
                    size={ButtonSize.SMALL}
                    icon={Icons.Append}
                    onClick={() => onShowAddTrackNumberDialog()}
                    qa-id="create-tracknumer-button"
                />
            </div>
            <ul className={styles['geometry-alignment-infobox__alignments-container']}>
                {referenceLines &&
                    referenceLines.map((line) => {
                        const key = getKey(line, undefined);
                        const isSelected =
                            key ===
                            getKey(selectedLayoutReferenceLine, selectedLayoutLocationTrack);
                        const selection = key ? alignmentRefs[key] : undefined;
                        const trackNumber = trackNumbers
                            ? trackNumbers.find((tn) => tn.id === line.trackNumberId)
                            : undefined;
                        if (!selection || !trackNumber) return '';
                        return (
                            <li
                                key={selection.key}
                                className={styles['geometry-alignment-infobox__alignment']}
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
                <InfoboxText value={t('tool-panel.alignment.geometry.choose-location-track')} />
                <Button
                    variant={ButtonVariant.GHOST}
                    size={ButtonSize.SMALL}
                    icon={Icons.Append}
                    onClick={() => onShowAddLocationTrackDialog()}
                    qa-id="create-location-track-button"
                />
            </div>
            <ul className={styles['geometry-alignment-infobox__alignments-container']}>
                {locationTracks &&
                    locationTracks.map((track) => {
                        const key = getKey(undefined, track);
                        const isSelected =
                            key ===
                            getKey(selectedLayoutReferenceLine, selectedLayoutLocationTrack);
                        const selection = key ? alignmentRefs[key] : undefined;
                        if (!selection) return '';
                        return (
                            <li
                                key={selection.key}
                                className={styles['geometry-alignment-infobox__alignment']}
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
    );
};
