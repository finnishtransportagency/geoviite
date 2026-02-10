import React from 'react';
import { useTranslation } from 'react-i18next';
import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import { OperationalPoint, LocationTrackId } from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { useLoader } from 'utils/react-utils';
import { getOperationalPointStationLinks } from 'track-layout/layout-operational-point-api';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { StationLinkView } from 'tool-panel/operational-point/station-link-view';
import styles from './operational-point-infobox.scss';

type OperationalPointStationLinksInfoboxProps = {
    contentVisible: boolean;
    onVisibilityChange: (key: 'stationLinks') => void;
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    changeTimes: ChangeTimes;
    onSelectLocationTrack: (locationTrackId: LocationTrackId) => void;
};

export const OperationalPointStationLinksInfobox: React.FC<
    OperationalPointStationLinksInfoboxProps
> = ({
    contentVisible,
    onVisibilityChange,
    layoutContext,
    operationalPoint,
    changeTimes,
    onSelectLocationTrack,
}) => {
    const { t } = useTranslation();

    const stationLinks = useLoader(
        () => getOperationalPointStationLinks(operationalPoint.id, layoutContext),
        [operationalPoint.id, layoutContext, changeTimes.layoutLocationTrack],
    );

    return (
        <Infobox
            title={t('tool-panel.operational-point.station-links.infobox-header')}
            contentVisible={contentVisible}
            onContentVisibilityChange={() => onVisibilityChange('stationLinks')}>
            {!stationLinks ? (
                <Spinner />
            ) : (
                <InfoboxContent>
                    {stationLinks.length === 0 ? (
                        <p className="infobox__text">
                            {t('tool-panel.operational-point.station-links.no-links')}
                        </p>
                    ) : (
                        <>
                            <p className="infobox__text">
                                {t('tool-panel.operational-point.station-links.count', {
                                    count: stationLinks.length,
                                })}
                            </p>
                            <ul className={styles['operational-point-infobox__station-links-list']}>
                                {stationLinks.map((stationLink) => (
                                    <li
                                        key={`${stationLink.startOperationalPointId}-${stationLink.endOperationalPointId}-${stationLink.trackNumberId}`}>
                                        <StationLinkView
                                            stationLink={stationLink}
                                            ownOperationalPointId={operationalPoint.id}
                                            layoutContext={layoutContext}
                                            changeTimes={changeTimes}
                                            onSelectLocationTrack={onSelectLocationTrack}
                                        />
                                    </li>
                                ))}
                            </ul>
                        </>
                    )}
                </InfoboxContent>
            )}
        </Infobox>
    );
};
