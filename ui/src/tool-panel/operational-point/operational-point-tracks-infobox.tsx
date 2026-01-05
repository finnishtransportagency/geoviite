import styles from './operational-point-infobox.scss';
import React from 'react';
import {
    LayoutLocationTrack,
    LocationTrackId,
    OperationalPoint,
    OperationalPointId,
} from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import Infobox from 'tool-panel/infobox/infobox';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import { ChangeTimes } from 'common/common-slice';
import { compareIgnoreCase } from 'utils/string-utils';
import { ShowMoreButton } from 'show-more-button/show-more-button';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import {
    createUseLinkingHook,
    getSuccessToastMessageParams,
    LinkingDirection,
} from 'tool-panel/operational-point/operational-point-utils';
import {
    findOperationalPointLocationTracks,
    getLocationTracks,
    linkLocationTrackToOperationalPoint,
    OperationalPointLocationTracks,
    unlinkLocationTrackToOperationalPoint,
} from 'track-layout/layout-location-track-api';
import { LocationTrackBadge } from 'geoviite-design-lib/alignment/location-track-badge';
import { filterUnique } from 'utils/array-utils';
import { updateLocationTrackChangeTime } from 'common/change-time-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import InfoboxContent from 'tool-panel/infobox/infobox-content';

type OperationalPointTracksInfoboxProps = {
    contentVisible: boolean;
    onVisibilityChange: (key: 'tracks') => void;
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    changeTimes: ChangeTimes;
};

const maxTracksToDisplay = 8;
export const OperationalPointTracksInfobox: React.FC<OperationalPointTracksInfoboxProps> = ({
    contentVisible,
    onVisibilityChange,
    layoutContext,
    operationalPoint,
    changeTimes,
}) => {
    const { t } = useTranslation();

    const { isInitializing, itemAssociation, linkedItems, unlinkedItems, linkItems, unlinkItems } =
        useLinkingTracks(layoutContext, operationalPoint, changeTimes);

    const tracksInOperationalPointPolygon = new Set(itemAssociation?.overlappingArea ?? []);

    return (
        <Infobox
            title={t('tool-panel.operational-point.track-links.infobox-header')}
            contentVisible={contentVisible}
            onContentVisibilityChange={() => onVisibilityChange('tracks')}>
            {isInitializing ? (
                <Spinner />
            ) : (
                <>
                    <OperationalPointTracksDirectionInfobox
                        layoutContext={layoutContext}
                        operationalPoint={operationalPoint}
                        changeTimes={changeTimes}
                        tracks={linkedItems}
                        linkingAction={unlinkItems}
                        linkingDirection={'unlinking'}
                        tracksInOperationalPointPolygon={tracksInOperationalPointPolygon}
                    />
                    <OperationalPointTracksDirectionInfobox
                        layoutContext={layoutContext}
                        operationalPoint={operationalPoint}
                        changeTimes={changeTimes}
                        tracks={unlinkedItems}
                        linkingAction={linkItems}
                        linkingDirection={'linking'}
                        tracksInOperationalPointPolygon={tracksInOperationalPointPolygon}
                    />
                </>
            )}
        </Infobox>
    );
};

const useLinkingTracks = createUseLinkingHook<
    LocationTrackId,
    LayoutLocationTrack,
    OperationalPointLocationTracks | undefined
>(
    async (context: LayoutContext, operationalPointId: OperationalPointId) => {
        const itemAssociation = await findOperationalPointLocationTracks(
            context,
            operationalPointId,
        );
        const trackIds = [
            ...(itemAssociation?.assigned ?? []),
            ...(itemAssociation?.overlappingArea ?? []),
        ].filter(filterUnique);
        const items = await getLocationTracks(trackIds, context);
        return { itemAssociation, items };
    },
    (lt) => lt.operationalPointIds,
    (changeTimes) => changeTimes.layoutLocationTrack,
    updateLocationTrackChangeTime,
    linkLocationTrackToOperationalPoint,
    unlinkLocationTrackToOperationalPoint,
);

type OperationalPointTracksDirectionInfoboxProps = {
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    changeTimes: ChangeTimes;
    tracks: LayoutLocationTrack[];
    tracksInOperationalPointPolygon: Set<LocationTrackId>;
    linkingAction: (
        tracks: {
            id: LocationTrackId;
            name: string;
        }[],
        idsToImmediatelyToss: LocationTrackId[],
    ) => Promise<string[]>;
    linkingDirection: LinkingDirection;
};

const OperationalPointTracksDirectionInfobox: React.FC<
    OperationalPointTracksDirectionInfoboxProps
> = ({
    operationalPoint,
    layoutContext,
    tracksInOperationalPointPolygon,
    tracks,
    linkingAction,
    linkingDirection,
}) => {
    const { t } = useTranslation();
    const [showAll, setShowAll] = React.useState(false);

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

    const idsToImmediatelyToss = validatedTracks
        .filter(({ issues }) => issues.some((issue) => issue.type === 'track-not-in-polygon'))
        .map(({ track }) => track.id);

    const linkAndToast: (
        tracks: {
            id: LocationTrackId;
            name: string;
        }[],
    ) => void = async (tracks) => {
        const linkedTracks = await linkingAction(tracks, idsToImmediatelyToss);
        const toastParams = getSuccessToastMessageParams(
            'track',
            operationalPoint.name,
            linkedTracks,
            linkingDirection,
        );
        if (toastParams) {
            Snackbar.success(t(toastParams[0], toastParams[1]));
        }
    };

    return (
        <InfoboxContent>
            <div className={styles['operational-point-linking-infobox__direction-title']}>
                {t(
                    `tool-panel.operational-point.track-links.${linkingDirection === 'linking' ? 'detached-in-polygon' : 'attached'}-header`,
                    { count: tracks.length },
                )}
            </div>
            {tracks.length > 0 && (
                <>
                    <div className={styles['operational-point-linking-infobox__direction-content']}>
                        {validatedTracks
                            .slice(0, showAll ? undefined : maxTracksToDisplay)
                            .map(({ track, issues }) => (
                                <OperationalPointTrackRow
                                    key={track.id}
                                    layoutContext={layoutContext}
                                    trackItem={track}
                                    issues={issues}
                                    linkingAction={linkAndToast}
                                    linkingDirection={linkingDirection}
                                />
                            ))}
                    </div>
                    <div>
                        {tracks.length > maxTracksToDisplay && (
                            <ShowMoreButton
                                expanded={showAll}
                                onShowMore={() => setShowAll((v) => !v)}
                                showMoreText={t(
                                    'tool-panel.operational-point.track-links.show-all',
                                    {
                                        count: tracks.length,
                                    },
                                )}
                            />
                        )}
                        <Button
                            variant={ButtonVariant.GHOST}
                            size={ButtonSize.SMALL}
                            onClick={() => linkAndToast(tracks)}>
                            {t(
                                `tool-panel.operational-point.track-links.${linkingDirection === 'linking' ? 'attach' : 'detach'}-all`,
                                { count: tracks.length },
                            )}
                        </Button>
                    </div>
                </>
            )}
        </InfoboxContent>
    );
};

type OperationalPointTrackRowProps = {
    layoutContext: LayoutContext;
    trackItem: LayoutLocationTrack;
    issues: TrackRowValidationIssue[];
    linkingAction: (tracks: { id: LocationTrackId; name: string }[]) => void;
    linkingDirection: LinkingDirection;
};

const OperationalPointTrackRow: React.FC<OperationalPointTrackRowProps> = ({
    trackItem,
    issues,
    linkingAction,
    linkingDirection,
}) => {
    return (
        <>
            <LocationTrackBadge locationTrack={trackItem} />
            <Button
                variant={ButtonVariant.GHOST}
                size={ButtonSize.SMALL}
                icon={linkingDirection === 'linking' ? Icons.Add : Icons.Subtract}
                onClick={() => linkingAction([trackItem])}
            />
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
