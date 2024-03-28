import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { formatDateShort } from 'utils/date-utils';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import Infobox from 'tool-panel/infobox/infobox';
import {
    useLocationTrack,
    useLocationTrackChangeTimes,
} from 'track-layout/track-layout-react-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { LayoutContext, officialLayoutContext } from 'common/common-model';
import { LocationTrackInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { useCommonDataAppSelector } from 'store/hooks';

type LocationTrackChangeInfoInfoboxProps = {
    locationTrackId: LocationTrackId;
    layoutContext: LayoutContext;
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    setConfirmingDraftDelete: (value: boolean) => void;
};

export const LocationTrackChangeInfoInfobox: React.FC<LocationTrackChangeInfoInfoboxProps> = ({
    locationTrackId,
    layoutContext,
    visibilities,
    visibilityChange,
    setConfirmingDraftDelete,
}) => {
    const { t } = useTranslation();
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const locationTrackChangeInfo = useLocationTrackChangeTimes(locationTrackId, layoutContext);
    const officialLocationTrack = useLocationTrack(
        locationTrackId,
        officialLayoutContext(layoutContext),
        changeTimes.layoutLocationTrack,
    );

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
                    {officialLocationTrack === undefined && (
                        <InfoboxButtons>
                            <Button
                                onClick={() => setConfirmingDraftDelete(true)}
                                icon={Icons.Delete}
                                variant={ButtonVariant.WARNING}
                                size={ButtonSize.SMALL}>
                                {t('button.delete-draft')}
                            </Button>
                        </InfoboxButtons>
                    )}
                </InfoboxContent>
            </Infobox>
        )
    );
};
