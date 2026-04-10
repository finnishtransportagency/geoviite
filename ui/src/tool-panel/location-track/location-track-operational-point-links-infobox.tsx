import React from 'react';
import { useTranslation } from 'react-i18next';
import Infobox from 'tool-panel/infobox/infobox';
import { LayoutLocationTrack } from 'track-layout/track-layout-model';
import { useLocationTrackInfoboxExtras } from 'track-layout/track-layout-react-utils';
import { LayoutContext } from 'common/common-model';
import styles from './location-track-operational-point-links-infobox.scss';
import { ChangeTimes } from 'common/common-slice';
import { OnSelectOptions } from 'selection/selection-model';
import { ShowMoreButton } from 'show-more-button/show-more-button';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { getManyOperationalPoints } from 'track-layout/layout-operational-point-api';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import { LocationTrackOperationalPointRow } from './location-track-operational-point-row';

const maxOperationalPointsToDisplay = 10;

type LocationTrackOperationalPointLinksInfoboxProps = {
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
    locationTrack: LayoutLocationTrack;
    layoutContext: LayoutContext;
    changeTimes: ChangeTimes;
    onSelect: (items: OnSelectOptions) => void;
};

export const LocationTrackOperationalPointLinksInfobox: React.FC<
    LocationTrackOperationalPointLinksInfoboxProps
> = ({
    contentVisible,
    onContentVisibilityChange,
    locationTrack,
    layoutContext,
    changeTimes,
    onSelect,
}) => {
    const { t } = useTranslation();

    const [infoboxExtras, infoboxExtrasLoadStatus] = useLocationTrackInfoboxExtras(
        locationTrack.id,
        layoutContext,
        changeTimes,
    );
    const operationalPointExtras = infoboxExtras?.operationalPoints ?? [];
    const opIds = operationalPointExtras.map((op) => op.operationalPointId);

    const [operationalPoints, linkedPointFetchStatus] = useLoaderWithStatus(
        () => getManyOperationalPoints(opIds, layoutContext, changeTimes.operationalPoints),
        [
            locationTrack.id,
            JSON.stringify(opIds),
            layoutContext.branch,
            layoutContext.publicationState,
            changeTimes.operationalPoints,
        ],
    );

    const [showAll, setShowAll] = React.useState(false);
    const operationalPointsToShow =
        (showAll
            ? operationalPoints
            : operationalPoints?.slice(0, maxOperationalPointsToDisplay)) ?? [];

    return (
        <Infobox
            title={t('tool-panel.location-track.operational-point-links.heading', {
                count: operationalPoints?.length ?? 0,
            })}
            qa-id={'location-track-operational-point-links-infobox'}
            contentVisible={contentVisible}
            onContentVisibilityChange={onContentVisibilityChange}>
            <ProgressIndicatorWrapper
                indicator={ProgressIndicatorType.Area}
                inProgress={
                    infoboxExtrasLoadStatus !== LoaderStatus.Ready ||
                    linkedPointFetchStatus !== LoaderStatus.Ready
                }
                inline={true}>
                <InfoboxContent>
                    {!!operationalPoints && operationalPoints.length > 0 ? (
                        <React.Fragment>
                            <div
                                className={
                                    styles['location-track-operational-point-links-infobox-list']
                                }>
                                {operationalPointsToShow.map((operationalPoint) => (
                                    <LocationTrackOperationalPointRow
                                        key={operationalPoint.id}
                                        operationalPoint={operationalPoint}
                                        address={
                                            operationalPointExtras?.find(
                                                (op) =>
                                                    op.operationalPointId === operationalPoint.id,
                                            )?.displayAddress
                                        }
                                        layoutContext={layoutContext}
                                        locationTrack={locationTrack}
                                        onSelect={onSelect}
                                    />
                                ))}
                            </div>
                            {operationalPoints.length > maxOperationalPointsToDisplay && (
                                <ShowMoreButton
                                    expanded={showAll}
                                    onShowMore={() => setShowAll(!showAll)}
                                    showMoreText={t(
                                        'tool-panel.location-track.operational-point-links.show-more',
                                    )}
                                />
                            )}
                        </React.Fragment>
                    ) : (
                        <p className={'infobox__text'}>
                            {t(
                                'tool-panel.location-track.operational-point-links.no-operational-points',
                            )}
                        </p>
                    )}
                </InfoboxContent>
            </ProgressIndicatorWrapper>
        </Infobox>
    );
};
