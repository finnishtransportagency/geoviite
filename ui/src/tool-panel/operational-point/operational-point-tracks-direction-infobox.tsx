import React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './operational-point-infobox.scss';
import { LayoutLocationTrack, LocationTrackId } from 'track-layout/track-layout-model';
import { Hide, LinkingDirection } from 'tool-panel/operational-point/operational-point-utils';
import { compareIgnoreCase } from 'utils/string-utils';
import { createClassName } from 'vayla-design-lib/utils';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { LocationTrackBadge } from 'geoviite-design-lib/alignment/location-track-badge';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';

type OperationalPointTracksDirectionInfoboxProps = {
    tracks: LayoutLocationTrack[];
    tracksInOperationalPointPolygon: Set<LocationTrackId>;
    linkingDirection: LinkingDirection;
    isEditing: boolean;
    linkingAction: (id: LocationTrackId) => void;
    massLinkingAction: () => void;
    onSelectLocationTrack: (locationTrackId: LocationTrackId) => void;
};

export const OperationalPointTracksDirectionInfobox: React.FC<
    OperationalPointTracksDirectionInfoboxProps
> = ({
    tracksInOperationalPointPolygon,
    tracks,
    linkingDirection,
    isEditing,
    linkingAction,
    massLinkingAction,
    onSelectLocationTrack,
}) => {
    const { t } = useTranslation();

    const validatedTracks = tracks
        .map((track) => ({
            track,
            issues: validateOperationalPointTrackRow(
                track,
                tracksInOperationalPointPolygon,
                linkingDirection,
            ),
        }))
        .sort((a, b) => {
            const issuesComp = (b.issues.length > 0 ? 1 : 0) - (a.issues.length > 0 ? 1 : 0);
            return issuesComp === 0 ? compareIgnoreCase(a.track.name, b.track.name) : issuesComp;
        });

    return (
        <>
            <div className={styles['operational-point-linking-infobox__direction-title']}>
                {t(
                    `tool-panel.operational-point.track-links.${linkingDirection === 'linking' ? 'detached-in-polygon' : 'attached'}-header`,
                    { count: tracks.length },
                )}
            </div>
            <>
                <div
                    className={createClassName(
                        styles[
                            'operational-point-linking-infobox__location-tracks-direction-content'
                        ],
                        validatedTracks.length === 0 &&
                            styles[
                                'operational-point-linking-infobox__location-tracks-direction-content--empty'
                            ],
                    )}>
                    {validatedTracks.length === 0 ? (
                        <InfoboxText
                            value={t(
                                `tool-panel.operational-point.track-links.none-for-${linkingDirection}`,
                            )}
                        />
                    ) : (
                        validatedTracks.map(({ track, issues }) => (
                            <OperationalPointTrackRow
                                key={track.id}
                                trackItem={track}
                                issues={issues}
                                linkingDirection={linkingDirection}
                                isEditing={isEditing}
                                linkingAction={linkingAction}
                                onSelectLocationTrack={onSelectLocationTrack}
                            />
                        ))
                    )}
                </div>
                <Hide when={!isEditing}>
                    <Button
                        variant={ButtonVariant.GHOST}
                        size={ButtonSize.SMALL}
                        onClick={massLinkingAction}>
                        {t(
                            `tool-panel.operational-point.track-links.${linkingDirection === 'linking' ? 'attach' : 'detach'}-all`,
                            { count: tracks.length },
                        )}
                    </Button>
                </Hide>
            </>
        </>
    );
};

type OperationalPointTrackRowProps = {
    trackItem: LayoutLocationTrack;
    issues: TrackRowValidationIssue[];
    linkingDirection: LinkingDirection;
    isEditing: boolean;
    linkingAction: (id: LocationTrackId) => void;
    onSelectLocationTrack: (locationTrackId: LocationTrackId) => void;
};

const OperationalPointTrackRow: React.FC<OperationalPointTrackRowProps> = ({
    trackItem,
    issues,
    linkingDirection,
    isEditing,
    linkingAction,
    onSelectLocationTrack,
}) => {
    return (
        <>
            <LocationTrackBadge
                locationTrack={trackItem}
                onClick={() => onSelectLocationTrack(trackItem.id)}
            />
            <Hide when={!isEditing}>
                <Button
                    variant={ButtonVariant.GHOST}
                    size={ButtonSize.SMALL}
                    icon={linkingDirection === 'linking' ? Icons.Ascending : Icons.Descending}
                    onClick={() => linkingAction(trackItem.id)}
                />
            </Hide>
            <div>
                {issues.slice(0, 1).map((issue, i) => (
                    <TrackRowValidationIssueBadge key={i} issue={issue} />
                ))}
            </div>
        </>
    );
};

const TrackRowValidationIssueBadge: React.FC<{
    issue: TrackRowValidationIssue;
}> = ({ issue }) => {
    const { t } = useTranslation();
    return (
        <div className={styles['operational-point-linking-infobox__validation-issue-badge']}>
            <Icons.StatusError color={IconColor.INHERIT} />
            {t(`tool-panel.operational-point.track-links.${issue.type}`)}
        </div>
    );
};

type TrackRowValidationIssue = { type: 'track-not-in-polygon' };

function validateOperationalPointTrackRow(
    track: LayoutLocationTrack,
    tracksInOperationalPointPolygon: Set<LocationTrackId>,
    linkingDirection: LinkingDirection,
): TrackRowValidationIssue[] {
    const notInPolygonProblem =
        linkingDirection === 'unlinking' && !tracksInOperationalPointPolygon.has(track.id)
            ? [{ type: 'track-not-in-polygon' } as const]
            : [];

    return [...notInPolygonProblem];
}
