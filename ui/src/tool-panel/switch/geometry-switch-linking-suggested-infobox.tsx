import * as React from 'react';
import { useTranslation } from 'react-i18next';
import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { LocationTrackEndpoint, SuggestedSwitch } from 'linking/linking-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { SwitchSuggestionCreatorDialog } from 'linking/switch/switch-suggestion-creator-dialog';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { getLocationTrack } from 'track-layout/layout-location-track-api';

type GeometrySwitchLinkingSuggestedInfoboxProps = {
    suggestedSwitch: SuggestedSwitch;
    alignmentEndPoint: LocationTrackEndpoint;
    onSuggestedSwitchChange: (suggestedSwitch: SuggestedSwitch) => void;
};

export const GeometrySwitchLinkingSuggestedInfobox: React.FC<
    GeometrySwitchLinkingSuggestedInfoboxProps
> = ({ suggestedSwitch, alignmentEndPoint, onSuggestedSwitchChange }) => {
    const { t } = useTranslation();
    const [location, setLocation] = React.useState<string>();
    const [showEditDialog, setShowEditDialog] = React.useState(false);

    React.useEffect(() => {
        getLocationTrack(alignmentEndPoint.locationTrackId, 'DRAFT').then((a) => {
            if (a) setLocation(a.name);
        });
    }, [alignmentEndPoint]);

    function onSelectSuggestedSwitch(suggestedSwitch: SuggestedSwitch) {
        onSuggestedSwitchChange(suggestedSwitch);
        setShowEditDialog(false);
    }

    return (
        <React.Fragment>
            <Infobox title={t('tool-panel.switch.geometry.suggested-switch-title')}>
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.switch.geometry.location')}
                        value={location}
                    />
                    <InfoboxField
                        label={t('tool-panel.switch.geometry.switch-structure-type')}
                        value={suggestedSwitch.switchStructure.type}
                    />
                    <InfoboxButtons>
                        <Button
                            variant={ButtonVariant.SECONDARY}
                            size={ButtonSize.SMALL}
                            onClick={() => setShowEditDialog(true)}>
                            {t('tool-panel.switch.geometry.modify-suggested-switch')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>

            {showEditDialog && (
                <SwitchSuggestionCreatorDialog
                    publishType="DRAFT"
                    locationTrackEndpoint={alignmentEndPoint}
                    suggestedSwitch={suggestedSwitch}
                    onSuggestedSwitchCreated={onSelectSuggestedSwitch}
                    onClose={() => setShowEditDialog(false)}
                />
            )}
        </React.Fragment>
    );
};

export default GeometrySwitchLinkingSuggestedInfobox;
