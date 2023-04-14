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

type GeometryAlignmentLinkingReferenceLineCandidatesProps = {
    geometryAlignment: MapAlignment;
    publishType: PublishType;
    trackNumberChangeTime: TimeStamp;
    selectedLayoutReferenceLine?: LayoutReferenceLine;
    linkingState?:
        | LinkingGeometryWithAlignment
        | LinkingGeometryWithEmptyAlignment
        | PreliminaryLinkingGeometry;
    onSelect: (options: OnSelectOptions) => void;
    onShowAddTrackNumberDialog: () => void;
};

type GeometryAlignmentLinkingLocationTrackCandidatesProps = {
    geometryAlignment: MapAlignment;
    publishType: PublishType;
    locationTrackChangeTime: TimeStamp;
    selectedLayoutLocationTrack?: LayoutLocationTrack;
    linkingState?:
        | LinkingGeometryWithAlignment
        | LinkingGeometryWithEmptyAlignment
        | PreliminaryLinkingGeometry;
    onSelect: (options: OnSelectOptions) => void;
    onShowAddLocationTrackDialog: () => void;
};

type AlignmentRef = {
    id: LocationTrackId | ReferenceLineId;
    ref: React.RefObject<HTMLLIElement>;
};

function createReference(id: ReferenceLineId | LocationTrackId): AlignmentRef {
    return {
        id: id,
        ref: React.createRef(),
    };
}

const NEAR_TRACK_SEARCH_BUFFER = 10.0;

export const GeometryAlignmentLinkingReferenceLineCandidates: React.FC<
    GeometryAlignmentLinkingReferenceLineCandidatesProps
> = ({
    geometryAlignment,
    publishType,
    trackNumberChangeTime,
    selectedLayoutReferenceLine,
    linkingState,
    onSelect,
    onShowAddTrackNumberDialog,
}) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbers(publishType, trackNumberChangeTime);
    const [referenceLineRefs, setReferenceLineRefs] = React.useState<AlignmentRef[]>([]);
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
    }, [geometryAlignment.boundingBox, trackNumberChangeTime]);

    React.useEffect(() => {
        const lines = referenceLines.map((rl) => createReference(rl.id));
        if (
            selectedLayoutReferenceLine &&
            !referenceLines.some((lt) => lt.id === selectedLayoutReferenceLine.id)
        ) {
            lines.push(createReference(selectedLayoutReferenceLine.id));
        }

        setReferenceLineRefs(lines);
    }, [selectedLayoutReferenceLine, referenceLines]);

    React.useEffect(() => {
        const ref = referenceLineRefs.find((r) => r.id == selectedLayoutReferenceLine?.id);

        if (ref) {
            ref.ref.current?.scrollIntoView({
                behavior: 'smooth',
                block: 'nearest',
            });
        }
    }, [selectedLayoutReferenceLine]);

    return (
        <React.Fragment>
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
                {referenceLines?.map((line) => {
                    const isSelected = line.id == selectedLayoutReferenceLine?.id;
                    const ref = referenceLineRefs.find((r) => r.id == line.id);
                    const trackNumber = trackNumbers?.find((tn) => tn.id === line.trackNumberId);
                    if (!ref || !trackNumber) return <></>;
                    return (
                        <li
                            key={ref.id}
                            className={styles['geometry-alignment-infobox__alignment']}
                            onClick={() =>
                                onSelect({
                                    trackNumbers: [line.trackNumberId],
                                    locationTracks: [],
                                })
                            }
                            ref={ref.ref}>
                            <ReferenceLineBadge
                                trackNumber={trackNumber}
                                status={
                                    isSelected
                                        ? ReferenceLineBadgeStatus.SELECTED
                                        : ReferenceLineBadgeStatus.DEFAULT
                                }
                            />
                            {linkingInProgress && linkingState.layoutAlignmentId === line.id && (
                                <Icons.Lock size={IconSize.SMALL} color={IconColor.INHERIT} />
                            )}
                        </li>
                    );
                })}
                {referenceLines?.length == 0 && (
                    <span className={styles['geometry-alignment-infobox__no-matches']}>
                        {t('tool-panel.alignment.geometry.no-linkable-reference-lines')}
                    </span>
                )}
            </ul>
        </React.Fragment>
    );
};

export const GeometryAlignmentLinkingLocationTrackCandidates: React.FC<
    GeometryAlignmentLinkingLocationTrackCandidatesProps
> = ({
    geometryAlignment,
    publishType,
    locationTrackChangeTime,
    selectedLayoutLocationTrack,
    linkingState,
    onSelect,
    onShowAddLocationTrackDialog,
}) => {
    const { t } = useTranslation();
    const [locationTrackRefs, setLocationTrackRefs] = React.useState<AlignmentRef[]>([]);
    const [locationTracks, setLocationTracks] = React.useState<LayoutLocationTrack[]>([]);

    const linkingInProgress = linkingState?.state === 'setup' || linkingState?.state === 'allSet';

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
        const tracks = locationTracks.map((lt) => createReference(lt.id));
        if (
            selectedLayoutLocationTrack &&
            !locationTracks.some((lt) => lt.id === selectedLayoutLocationTrack.id)
        ) {
            tracks.push(createReference(selectedLayoutLocationTrack.id));
        }

        setLocationTrackRefs(tracks);
    }, [selectedLayoutLocationTrack, locationTracks]);

    React.useEffect(() => {
        const ref = locationTrackRefs.find((r) => r.id == selectedLayoutLocationTrack?.id);
        if (ref) {
            ref.ref.current?.scrollIntoView({
                behavior: 'smooth',
                block: 'nearest',
            });
        }
    }, [selectedLayoutLocationTrack]);

    return (
        <div className={styles['geometry-alignment-infobox__linking-container']}>
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
                {locationTracks?.map((track) => {
                    const isSelected = track.id == selectedLayoutLocationTrack?.id;
                    const ref = locationTrackRefs.find((r) => r.id == track.id);
                    if (!ref) return <></>;
                    return (
                        <li
                            key={ref.id}
                            className={styles['geometry-alignment-infobox__alignment']}
                            onClick={() =>
                                onSelect({
                                    trackNumbers: [],
                                    locationTracks: [track.id],
                                })
                            }
                            ref={ref.ref}>
                            <LocationTrackBadge
                                locationTrack={track}
                                status={
                                    isSelected
                                        ? LocationTrackBadgeStatus.SELECTED
                                        : LocationTrackBadgeStatus.DEFAULT
                                }
                            />
                            {linkingInProgress && linkingState.layoutAlignmentId === track.id && (
                                <Icons.Lock size={IconSize.SMALL} color={IconColor.INHERIT} />
                            )}
                            <span>
                                <LocationTrackTypeLabel type={track.type} />
                            </span>
                        </li>
                    );
                })}
                {locationTracks?.length == 0 && (
                    <span className={styles['geometry-alignment-infobox__no-matches']}>
                        {t('tool-panel.alignment.geometry.no-linkable-location-tracks')}
                    </span>
                )}
            </ul>
        </div>
    );
};
