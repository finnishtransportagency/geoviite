import * as React from 'react';
import styles from './track-number-infobox.scss';
import infoboxStyles from 'tool-panel/infobox/infobox.module.scss';
import Infobox from 'tool-panel/infobox/infobox';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import { useTranslation } from 'react-i18next';
import { LocationTrackLink } from 'tool-panel/location-track/location-track-link';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { InfoboxList, InfoboxListRow } from 'tool-panel/infobox/infobox-list';
import { compareNamed, LayoutContext, TimeStamp } from 'common/common-model';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import {
    getLocationTrackIdsByTrackNumber,
    getLocationTracks,
} from 'track-layout/layout-location-track-api';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';

type TrackNumberLocationTrackInfoboxProps = {
    trackNumberId: LayoutTrackNumberId;
    layoutContext: LayoutContext;
    visibleLocationTrackIds: LocationTrackId[];
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
    changeTime: TimeStamp;
};

const TrackNumberLocationTrackInfoboxM: React.FC<TrackNumberLocationTrackInfoboxProps> = ({
    trackNumberId,
    layoutContext,
    visibleLocationTrackIds,
    contentVisible,
    onContentVisibilityChange,
    changeTime,
}) => {
    const { t } = useTranslation();
    const [filterByVisibleArea, setFilterByVisibleArea] = React.useState(true);

    const [locationTracks, locationTrackFetchStatus] = useLoaderWithStatus(
        () =>
            getLocationTrackIdsByTrackNumber(trackNumberId, layoutContext)
                .then((ids) => getLocationTracks(ids, layoutContext))
                .then((tracks) => tracks.toSorted(compareNamed)),
        [trackNumberId, layoutContext, changeTime],
    );

    const visibleLocationTracks =
        (filterByVisibleArea
            ? locationTracks?.filter((lt) => visibleLocationTrackIds.includes(lt.id))
            : locationTracks) ?? [];

    return (
        <Infobox
            contentVisible={contentVisible}
            onContentVisibilityChange={onContentVisibilityChange}
            title={t('tool-panel.track-number.location-tracks-title', {
                count: locationTracks?.length || 0,
            })}
            qa-id="track-number-location-tracks-infobox">
            <InfoboxContent>
                <ProgressIndicatorWrapper
                    indicator={ProgressIndicatorType.Area}
                    inProgress={locationTrackFetchStatus !== LoaderStatus.Ready}
                    inline={false}>
                    <InfoboxList>
                        <InfoboxListRow
                            label={t('tool-panel.track-number.location-tracks-filter-by-area')}
                            content={
                                <Checkbox
                                    checked={filterByVisibleArea}
                                    onChange={(e) => setFilterByVisibleArea(e.target.checked)}
                                />
                            }
                        />
                    </InfoboxList>
                    {visibleLocationTracks?.length > 0 ? (
                        <ul className={styles['track-number-infobox__location-tracks-list']}>
                            {visibleLocationTracks?.map((lt) => (
                                <li key={lt.id}>
                                    <span
                                        className={
                                            styles['track-number-infobox__location-track-name']
                                        }>
                                        <LocationTrackLink
                                            locationTrackId={lt.id}
                                            locationTrackName={lt.name}
                                        />
                                    </span>
                                    <span
                                        className={
                                            styles[
                                                'track-number-infobox__location-track-description'
                                            ]
                                        }
                                        title={lt.description}>
                                        {lt.description}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <p className={infoboxStyles['infobox__text']}>
                            {t('tool-panel.track-number.no-location-tracks')}
                        </p>
                    )}
                </ProgressIndicatorWrapper>
            </InfoboxContent>
        </Infobox>
    );
};

export const TrackNumberLocationTrackInfobox = React.memo(TrackNumberLocationTrackInfoboxM);
