import React from 'react';
import { useTranslation } from 'react-i18next';
import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import { OperationalPoint, StationLinkIssue, StationLink } from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { getOperationalPointStationLinks } from 'track-layout/layout-operational-point-api';
import { StationLinkView } from 'tool-panel/operational-point/station-link-view';
import styles from './operational-point-infobox.scss';
import { getMaxTimestamp } from 'utils/date-utils';
import { useTrackLayoutAppSelector } from 'store/hooks';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';

type OperationalPointStationLinksInfoboxProps = {
    contentVisible: boolean;
    onVisibilityChange: (key: 'stationLinks') => void;
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    changeTimes: ChangeTimes;
};

export const OperationalPointStationLinksInfobox: React.FC<
    OperationalPointStationLinksInfoboxProps
> = ({ contentVisible, onVisibilityChange, layoutContext, operationalPoint, changeTimes }) => {
    const { t } = useTranslation();
    const linkingState = useTrackLayoutAppSelector((state) => state.linkingState);
    const splittingState = useTrackLayoutAppSelector((state) => state.splittingState);
    const isLinkingOrSplitting = !!linkingState || !!splittingState;

    const changeTime = getMaxTimestamp(
        changeTimes.layoutLocationTrack,
        changeTimes.layoutSwitch,
        changeTimes.operationalPoints,
    );

    const [stationLinks, stationLinksFetchStatus] = useLoaderWithStatus(
        () => getOperationalPointStationLinks(operationalPoint.id, layoutContext, changeTime),
        [operationalPoint.id, layoutContext, changeTime],
    );

    const stationLinkIssueMap = stationLinks?.issues
        .filter(
            (issue) =>
                issue.operationalPointId === operationalPoint.id ||
                issue.otherOperationalPointId === operationalPoint.id,
        )
        .reduce((acc, issue) => {
            const key =
                issue.operationalPointId === operationalPoint.id
                    ? `${issue.operationalPointId}-${issue.otherOperationalPointId}`
                    : `${issue.otherOperationalPointId}-${issue.operationalPointId}`;
            const previous = acc.get(key);
            acc.set(key, previous ? [...previous, issue] : [issue]);
            return acc;
        }, new Map<string, StationLinkIssue[]>());

    const stationLinkMap = stationLinks?.links.reduce((acc, link) => {
        const key =
            link.startOperationalPointId === operationalPoint.id
                ? `${link.startOperationalPointId}-${link.endOperationalPointId}`
                : `${link.endOperationalPointId}-${link.startOperationalPointId}`;
        acc.set(key, {
            link: link,
            issues: stationLinkIssueMap?.get(key) ?? [],
        });
        stationLinkIssueMap?.delete(key);
        return acc;
    }, new Map<string, StationLinkData>());

    // Include any remaining issues that don't have a corresponding link
    stationLinkIssueMap?.forEach((issues, key) => {
        stationLinkMap?.set(key, { link: undefined, issues: issues });
    });

    return (
        <Infobox
            title={t('tool-panel.operational-point.station-links.infobox-header', {
                count: stationLinks?.links.length ?? 0,
            })}
            contentVisible={contentVisible}
            onContentVisibilityChange={() => onVisibilityChange('stationLinks')}>
            {
                <ProgressIndicatorWrapper
                    indicator={ProgressIndicatorType.Area}
                    inProgress={stationLinksFetchStatus !== LoaderStatus.Ready}>
                    {stationLinkMap?.size === 0 ? (
                        <InfoboxContent>
                            <p className="infobox__text">
                                {t('tool-panel.operational-point.station-links.no-links')}
                            </p>
                        </InfoboxContent>
                    ) : (
                        <ul className={styles['operational-point-infobox__station-links-list']}>
                            {Array.from(stationLinkMap?.entries() ?? []).map(
                                ([stationLinkKey, { link, issues }]) => (
                                    <li key={stationLinkKey}>
                                        <StationLinkView
                                            stationLink={link}
                                            ownOperationalPointId={operationalPoint.id}
                                            layoutContext={layoutContext}
                                            changeTimes={changeTimes}
                                            isLinkingOrSplitting={isLinkingOrSplitting}
                                            issues={issues}
                                        />
                                    </li>
                                ),
                            )}
                        </ul>
                    )}
                </ProgressIndicatorWrapper>
            }
        </Infobox>
    );
};

type StationLinkData = {
    link: StationLink | undefined;
    issues: StationLinkIssue[];
};
