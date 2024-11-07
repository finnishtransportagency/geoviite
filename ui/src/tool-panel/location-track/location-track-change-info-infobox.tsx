import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { formatDateShort } from 'utils/date-utils';
import Infobox from 'tool-panel/infobox/infobox';
import { useLocationTrackChangeTimes } from 'track-layout/track-layout-react-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import { LocationTrackInfoboxVisibilities } from 'track-layout/track-layout-slice';

type LocationTrackChangeInfoInfoboxProps = {
    locationTrackId: LocationTrackId;
    layoutContext: LayoutContext;
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
};

export const LocationTrackChangeInfoInfobox: React.FC<LocationTrackChangeInfoInfoboxProps> = ({
    locationTrackId,
    layoutContext,
    visibilities,
    visibilityChange,
}) => {
    const { t } = useTranslation();
    const locationTrackChangeInfo = useLocationTrackChangeTimes(locationTrackId, layoutContext);

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
                            locationTrackChangeInfo.changed &&
                            formatDateShort(locationTrackChangeInfo.changed)
                        }
                    />
                </InfoboxContent>
            </Infobox>
        )
    );
};
