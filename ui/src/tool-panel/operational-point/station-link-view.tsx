import React from 'react';
import { useTranslation } from 'react-i18next';
import {
    OperationalPointId,
    StationLink,
    StationLinkIssue,
    StationLinkIssueType,
} from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { useLocationTracks, useOperationalPoint } from 'track-layout/track-layout-react-utils';
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
import { createClassName } from 'vayla-design-lib/utils';
import { MessageBox, MessageBoxType } from 'geoviite-design-lib/message-box/message-box';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';

export type StationLinkViewProps = {
    stationLink: StationLink | undefined;
    issues: StationLinkIssue[];
    ownOperationalPointId?: OperationalPointId;
    layoutContext: LayoutContext;
    changeTimes: ChangeTimes;
    isLinkingOrSplitting: boolean;
};

export const StationLinkView: React.FC<StationLinkViewProps> = ({
    stationLink,
    issues,
    ownOperationalPointId,
    layoutContext,
    changeTimes,
    isLinkingOrSplitting,
}) => {
    const { t } = useTranslation();
    const locationTrackIds = stationLink
        ? [...new Set(stationLink.locationTrackIds)]
        : [
              ...new Set(
                  issues
                      .filter((issue) => issue.locationTrackId !== undefined)
                      .map((issue) => issue.locationTrackId!),
              ),
          ];

    const locationTracks = useLocationTracks(
        locationTrackIds,
        layoutContext,
        changeTimes.layoutLocationTrack,
    );

    const operationalPointIds = (() => {
        if (stationLink) {
            return ownOperationalPointId &&
                stationLink.endOperationalPointId === ownOperationalPointId
                ? [stationLink.endOperationalPointId, stationLink.startOperationalPointId]
                : [stationLink.startOperationalPointId, stationLink.endOperationalPointId];
        }

        const firstIssue = issues[0];
        if (!firstIssue?.otherOperationalPointId) return undefined;

        return ownOperationalPointId && firstIssue.otherOperationalPointId === ownOperationalPointId
            ? [firstIssue.otherOperationalPointId, firstIssue.operationalPointId]
            : [firstIssue.operationalPointId, firstIssue.otherOperationalPointId];
    })();

    if (!operationalPointIds) return null;
    const [firstOp, secondOp] = operationalPointIds;

    const first = useOperationalPoint(firstOp, layoutContext, changeTimes.operationalPoints);
    const second = useOperationalPoint(secondOp, layoutContext, changeTimes.operationalPoints);

    const trackNumberId = stationLink?.trackNumberId ?? issues[0]?.trackNumberId;
    const trackLength = stationLink?.length;

    const getErrorText = (issue: StationLinkIssue) => {
        switch (issue.type) {
            case StationLinkIssueType.UNREACHABLE_STATION_MIDPOINT:
                return t(
                    'tool-panel.operational-point.station-links.issue-unreachable-station-midpoint',
                    { name: issue.operationalPointId === firstOp ? first?.name : second?.name },
                );
            case StationLinkIssueType.SUSPICIOUSLY_LONG_ROUTE:
                return t(
                    'tool-panel.operational-point.station-links.issue-suspiciously-long-route',
                    { name: issue.operationalPointId === firstOp ? first?.name : second?.name },
                );
        }
    };

    return (
        <>
            <InfoboxContent>
                <div className={styles['operational-point-infobox__station-link-header']}>
                    <span className={styles['operational-point-infobox__station-link-name']}>
                        <OperationalPointBadgeLink
                            operationalPointId={firstOp!}
                            layoutContext={layoutContext}
                            changeTime={changeTimes.operationalPoints}
                            disabled={isLinkingOrSplitting}
                        />
                        <Icons.Next size={IconSize.SMALL} color={IconColor.INHERIT} />
                        <OperationalPointBadgeLink
                            operationalPointId={secondOp!}
                            layoutContext={layoutContext}
                            changeTime={changeTimes.operationalPoints}
                            disabled={isLinkingOrSplitting}
                        />
                    </span>
                    {trackNumberId && (
                        <TrackNumberBadgeLink
                            trackNumberId={trackNumberId}
                            layoutContext={layoutContext}
                            changeTime={changeTimes.layoutTrackNumber}
                            status={
                                isLinkingOrSplitting ? TrackNumberBadgeStatus.DISABLED : undefined
                            }
                        />
                    )}
                    {trackLength ? (
                        <span
                            className={createClassName(
                                styles['operational-point-infobox__station-link-length'],
                            )}>
                            {Math.round(trackLength)} m
                        </span>
                    ) : (
                        <span
                            className={createClassName(
                                styles['operational-point-infobox__station-link-length__error'],
                            )}
                            title={t(
                                'tool-panel.operational-point.station-links.route-length-not-calculable',
                            )}>
                            <Icons.StatusError color={IconColor.INHERIT} />
                        </span>
                    )}
                </div>
            </InfoboxContent>
            {locationTracks.length > 0 && (
                <InfoboxContent>
                    <div className={styles['operational-point-infobox__station-link-tracks']}>
                        {locationTracks.map((track) => (
                            <LocationTrackBadge
                                key={track.id}
                                locationTrack={track}
                                status={
                                    isLinkingOrSplitting
                                        ? LocationTrackBadgeStatus.DISABLED
                                        : undefined
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
                </InfoboxContent>
            )}
            {issues.length > 0 &&
                issues.map((issue) => (
                    <InfoboxContentSpread
                        key={`${issue.operationalPointId}-${issue.otherOperationalPointId}-${issue.type}`}>
                        <MessageBox type={messageBoxType(issue.type)}>
                            {getErrorText(issue)}
                        </MessageBox>
                    </InfoboxContentSpread>
                ))}
        </>
    );
};

const messageBoxType = (issueType: StationLinkIssueType): MessageBoxType => {
    switch (issueType) {
        case StationLinkIssueType.UNREACHABLE_STATION_MIDPOINT:
            return MessageBoxType.ERROR;
        case StationLinkIssueType.SUSPICIOUSLY_LONG_ROUTE:
            return MessageBoxType.WARNING;
    }
};
