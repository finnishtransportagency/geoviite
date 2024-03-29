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
import { draftLayoutContext, LayoutContext, SwitchStructure } from 'common/common-model';

type GeometrySwitchLinkingSuggestedInfoboxProps = {
    layoutContext: LayoutContext;
    suggestedSwitch: SuggestedSwitch;
    suggestedSwitchStructure: SwitchStructure;
    alignmentEndPoint: LocationTrackEndpoint;
    onSuggestedSwitchChange: (suggestedSwitch: SuggestedSwitch) => void;
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
};

export const GeometrySwitchLinkingSuggestedInfobox: React.FC<
    GeometrySwitchLinkingSuggestedInfoboxProps
> = ({
    layoutContext,
    suggestedSwitch,
    suggestedSwitchStructure,
    alignmentEndPoint,
    onSuggestedSwitchChange,
    contentVisible,
    onContentVisibilityChange,
}) => {
    const { t } = useTranslation();
    const [location, setLocation] = React.useState<string>();
    const [showEditDialog, setShowEditDialog] = React.useState(false);

    React.useEffect(() => {
        getLocationTrack(alignmentEndPoint.locationTrackId, draftLayoutContext(layoutContext)).then(
            (a) => {
                if (a) setLocation(a.name);
            },
        );
    }, [alignmentEndPoint]);

    function onSelectSuggestedSwitch(suggestedSwitch: SuggestedSwitch) {
        onSuggestedSwitchChange(suggestedSwitch);
        setShowEditDialog(false);
    }

    return (
        <React.Fragment>
            <Infobox
                contentVisible={contentVisible}
                onContentVisibilityChange={onContentVisibilityChange}
                title={t('tool-panel.switch.geometry.suggested-switch-title')}>
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.switch.geometry.location')}
                        value={location}
                    />
                    <InfoboxField
                        label={t('tool-panel.switch.geometry.switch-structure-type')}
                        value={suggestedSwitchStructure.type}
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
                    layoutContext={draftLayoutContext(layoutContext)}
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
