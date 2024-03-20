import * as React from 'react';
import { useTranslation } from 'react-i18next';
import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { useLocationTrack } from 'track-layout/track-layout-react-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { LocationTrackInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { useCommonDataAppSelector } from 'store/hooks';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_LAYOUT } from 'user/user-model';
import { LayoutContext, officialLayoutContext } from 'common/common-model';

type LocationTrackRatkoSyncInfoboxProps = {
    layoutContext: LayoutContext;
    locationTrackId: LocationTrackId;
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    setShowRatkoPushDialog: (showing: boolean) => void;
};

export const LocationTrackRatkoSyncInfobox: React.FC<LocationTrackRatkoSyncInfoboxProps> = ({
    layoutContext,
    locationTrackId,
    visibilities,
    visibilityChange,
    setShowRatkoPushDialog,
}) => {
    const { t } = useTranslation();
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const officialLocationTrack = useLocationTrack(
        locationTrackId,
        officialLayoutContext(layoutContext),
        changeTimes.layoutLocationTrack,
    );
    return officialLocationTrack ? (
        <PrivilegeRequired privilege={EDIT_LAYOUT}>
            <Infobox
                contentVisible={visibilities.ratkoPush}
                onContentVisibilityChange={() => visibilityChange('ratkoPush')}
                title={t('tool-panel.location-track.ratko-info-heading')}
                qa-id="location-track-ratko-infobox">
                <InfoboxContent>
                    <InfoboxButtons>
                        <Button
                            onClick={() => setShowRatkoPushDialog(true)}
                            variant={ButtonVariant.SECONDARY}
                            size={ButtonSize.SMALL}>
                            {t('tool-panel.location-track.push-to-ratko')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>
        </PrivilegeRequired>
    ) : (
        <React.Fragment />
    );
};
