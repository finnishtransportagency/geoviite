import styles from './operational-point-infobox.scss';
import React, { useState } from 'react';
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
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import {
    createUseLinkingHook,
    formatLinkingToast,
    Hide,
    LinkingDirection,
} from 'tool-panel/operational-point/operational-point-utils';
import {
    findOperationalPointLocationTracks,
    getLocationTracks,
    linkLocationTracksToOperationalPoint,
    OperationalPointLocationTracks,
    unlinkLocationTracksFromOperationalPoint,
} from 'track-layout/layout-location-track-api';
import { LocationTrackBadge } from 'geoviite-design-lib/alignment/location-track-badge';
import { filterUnique } from 'utils/array-utils';
import { updateLocationTrackChangeTime } from 'common/change-time-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { createClassName } from 'vayla-design-lib/utils';

type OperationalPointTracksInfoboxProps = {
    contentVisible: boolean;
    onVisibilityChange: (key: 'tracks') => void;
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    changeTimes: ChangeTimes;
};

export const OperationalPointTracksInfobox: React.FC<OperationalPointTracksInfoboxProps> = ({
    contentVisible,
    onVisibilityChange,
    layoutContext,
    operationalPoint,
    changeTimes,
}) => {
    const { t } = useTranslation();

    const {
        isInitializing,
        itemAssociation,
        linkedItems,
        unlinkedItems,
        isEditing,
        hasChanges,
        startEditing,
        cancelEditing,
        saveEdits,
        setLinks,
    } = useLinkingTracks(layoutContext, operationalPoint, changeTimes);

    const tracksInOperationalPointPolygon = new Set(itemAssociation?.overlappingArea ?? []);

    const [isSaving, setIsSaving] = useState(false);

    const handleSave = async () => {
        setIsSaving(true);
        try {
            const { linkedNames, unlinkedNames } = await saveEdits();
            const toastMessage = formatLinkingToast(
                linkedNames,
                unlinkedNames,
                t,
                'tool-panel.operational-point.track-links',
                operationalPoint.name,
            );
            if (toastMessage) {
                Snackbar.success(toastMessage);
            }
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <Infobox
            title={t('tool-panel.operational-point.track-links.infobox-header')}
            contentVisible={contentVisible}
            onContentVisibilityChange={() => onVisibilityChange('tracks')}>
            {isInitializing ? (
                <Spinner />
            ) : (
                <InfoboxContent>
                    <OperationalPointTracksDirectionInfobox
                        tracksInOperationalPointPolygon={tracksInOperationalPointPolygon}
                        tracks={linkedItems}
                        linkingDirection={'unlinking'}
                        isEditing={isEditing}
                        setLinks={setLinks}
                    />
                    <OperationalPointTracksDirectionInfobox
                        tracksInOperationalPointPolygon={tracksInOperationalPointPolygon}
                        tracks={unlinkedItems}
                        linkingDirection={'linking'}
                        isEditing={isEditing}
                        setLinks={setLinks}
                    />
                    <div className={styles['operational-point-linking-infobox__edit-buttons']}>
                        {!isEditing ? (
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                size={ButtonSize.SMALL}
                                onClick={startEditing}>
                                {t('tool-panel.operational-point.edit-links')}
                            </Button>
                        ) : (
                            <>
                                <Button
                                    variant={ButtonVariant.SECONDARY}
                                    size={ButtonSize.SMALL}
                                    disabled={isSaving}
                                    onClick={cancelEditing}>
                                    {t('tool-panel.operational-point.cancel-editing-links')}
                                </Button>
                                <Button
                                    variant={ButtonVariant.PRIMARY}
                                    size={ButtonSize.SMALL}
                                    disabled={!hasChanges || isSaving}
                                    onClick={handleSave}>
                                    {t('tool-panel.operational-point.save-links')}
                                </Button>
                                {isSaving && <Spinner />}
                            </>
                        )}
                    </div>
                </InfoboxContent>
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
    linkLocationTracksToOperationalPoint,
    unlinkLocationTracksFromOperationalPoint,
);

type OperationalPointTracksDirectionInfoboxProps = {
    tracks: LayoutLocationTrack[];
    tracksInOperationalPointPolygon: Set<LocationTrackId>;
    linkingDirection: LinkingDirection;
    isEditing: boolean;
    setLinks: (ids: LocationTrackId[], direction: LinkingDirection) => void;
};

const OperationalPointTracksDirectionInfobox: React.FC<
    OperationalPointTracksDirectionInfoboxProps
> = ({ tracksInOperationalPointPolygon, tracks, linkingDirection, isEditing, setLinks }) => {
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

    const handleSetLinksAll = () => {
        setLinks(
            tracks.map((t) => t.id),
            linkingDirection,
        );
    };

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
                                setLinks={setLinks}
                            />
                        ))
                    )}
                </div>
                <Hide when={!isEditing}>
                    <Button
                        variant={ButtonVariant.GHOST}
                        size={ButtonSize.SMALL}
                        onClick={handleSetLinksAll}>
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
    setLinks: (ids: LocationTrackId[], direction: LinkingDirection) => void;
};

const OperationalPointTrackRow: React.FC<OperationalPointTrackRowProps> = ({
    trackItem,
    issues,
    linkingDirection,
    isEditing,
    setLinks,
}) => {
    return (
        <>
            <LocationTrackBadge locationTrack={trackItem} />
            <Hide when={!isEditing}>
                <Button
                    variant={ButtonVariant.GHOST}
                    size={ButtonSize.SMALL}
                    icon={linkingDirection === 'linking' ? Icons.Add : Icons.Subtract}
                    onClick={() => setLinks([trackItem.id], linkingDirection)}
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
