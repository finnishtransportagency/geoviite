import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { formatDateShort } from 'utils/date-utils';
import Infobox from 'tool-panel/infobox/infobox';
import { useLocationTrackChangeTimes } from 'track-layout/track-layout-react-utils';
import { LayoutLocationTrack } from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import { LocationTrackInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { useNavigate } from 'react-router-dom';
import { SearchItemType } from 'asset-search/search-dropdown';
import { useHasPublicationLog } from 'publication/publication-utils';
import { getLocationTrack } from 'track-layout/layout-location-track-api';
import { publicationLogUrlForItem } from 'publication/log/publication-log-params';

type LocationTrackChangeInfoInfoboxProps = {
    locationTrack: LayoutLocationTrack;
    layoutContext: LayoutContext;
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
};

export const LocationTrackChangeInfoInfobox: React.FC<LocationTrackChangeInfoInfoboxProps> = ({
    locationTrack,
    layoutContext,
    visibilities,
    visibilityChange,
}) => {
    const { t } = useTranslation();
    const locationTrackId = locationTrack.id;
    const locationTrackChangeInfo = useLocationTrackChangeTimes(locationTrackId, layoutContext);

    const navigate = useNavigate();

    const hasPublicationLog = useHasPublicationLog(
        locationTrackId,
        getLocationTrack,
        locationTrackChangeInfo?.changed,
    );

    const openPublicationLogButtonTitle = hasPublicationLog
        ? undefined
        : t('tool-panel.location-track.publication-log-unavailable');

    const openPublicationLog = React.useCallback(() => {
        navigate(publicationLogUrlForItem({ type: SearchItemType.LOCATION_TRACK, locationTrack }));
    }, [locationTrack, navigate]);

    return (
        locationTrackChangeInfo && (
            <Infobox
                contentVisible={visibilities.log}
                onContentVisibilityChange={() => visibilityChange('log')}
                title={t('tool-panel.location-track.change-info-heading')}
                qa-id="location-track-log-infobox">
                <InfoboxContent>
                    <InfoboxField
                        qaId="location-track-created-date"
                        label={t('tool-panel.created')}
                        value={formatDateShort(locationTrackChangeInfo.created)}
                    />
                    <InfoboxField
                        qaId="location-track-changed-date"
                        label={t('tool-panel.changed')}
                        value={
                            locationTrackChangeInfo.changed
                                ? formatDateShort(locationTrackChangeInfo.changed)
                                : t('tool-panel.unmodified-in-geoviite')
                        }
                    />
                    <div>
                        <Button
                            size={ButtonSize.SMALL}
                            variant={ButtonVariant.SECONDARY}
                            title={openPublicationLogButtonTitle}
                            disabled={!hasPublicationLog}
                            onClick={openPublicationLog}>
                            {t('tool-panel.show-in-publication-log')}
                        </Button>
                    </div>
                </InfoboxContent>
            </Infobox>
        )
    );
};
