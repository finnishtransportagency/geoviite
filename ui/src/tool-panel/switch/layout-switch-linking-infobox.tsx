import { SuggestedSwitch } from 'linking/linking-model';
import { LayoutSwitch } from 'track-layout/track-layout-model';
import Infobox from 'tool-panel/infobox/infobox';
import { useTranslation } from 'react-i18next';
import { SwitchLinkingInfoboxVisibilities } from 'track-layout/track-layout-slice';
import React from 'react';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { SwitchBadge, SwitchBadgeStatus } from 'geoviite-design-lib/switch/switch-badge';
import { SwitchJointInfoboxContainer } from 'tool-panel/switch/switch-joint-infobox-container';
import { useSwitchStructure } from 'track-layout/track-layout-react-utils';
import { LayoutContext } from 'common/common-model';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import { MessageBox, MessageBoxType } from 'geoviite-design-lib/message-box/message-box';
import { linkSwitch } from 'linking/linking-api';
import * as SnackBar from 'geoviite-design-lib/snackbar/snackbar';

type LayoutSwitchLinkingInfoboxProps = {
    layoutSwitch: LayoutSwitch;
    suggestedSwitch: SuggestedSwitch;
    visibilities: SwitchLinkingInfoboxVisibilities;
    onVisibilityChange: (visibilities: SwitchLinkingInfoboxVisibilities) => void;
    layoutContext: LayoutContext;
    onStopLinking: () => void;
};

export const LayoutSwitchLinkingInfobox: React.FC<LayoutSwitchLinkingInfoboxProps> = ({
    layoutSwitch,
    suggestedSwitch,
    visibilities,
    onVisibilityChange,
    layoutContext,
    onStopLinking,
}) => {
    const { t } = useTranslation();
    const switchStructure = useSwitchStructure(layoutSwitch.switchStructureId);
    const canLink = layoutSwitch.stateCategory !== 'NOT_EXISTING';
    const [linkingCallInProgress, setLinkingCallInProgress] = React.useState(false);

    async function link() {
        setLinkingCallInProgress(true);
        try {
            await linkSwitch(
                layoutContext.branch,
                suggestedSwitch,
                layoutSwitch.id,
                layoutSwitch.sourceId,
            );
            SnackBar.success('tool-panel.switch.geometry.linking-succeed-msg');
            onStopLinking();
        } finally {
            setLinkingCallInProgress(false);
        }
    }

    return (
        <React.Fragment>
            <Infobox
                contentVisible={visibilities.linking}
                onContentVisibilityChange={() =>
                    onVisibilityChange({
                        ...visibilities,
                        linking: !visibilities.linking,
                    })
                }
                title={t('tool-panel.switch.geometry.linking-header')}
                qa-id="layout-switch-linking-infobox">
                <InfoboxContent>
                    <InfoboxField label={t('tool-panel.switch.geometry.predefined-switch')}>
                        <SwitchBadge
                            switchItem={layoutSwitch}
                            status={SwitchBadgeStatus.SELECTED}
                        />
                    </InfoboxField>
                    {switchStructure && (
                        <SwitchJointInfoboxContainer
                            suggestedSwitch={suggestedSwitch}
                            suggestedSwitchStructure={switchStructure}
                            layoutContext={layoutContext}
                        />
                    )}
                    <InfoboxButtons>
                        <Button
                            size={ButtonSize.SMALL}
                            variant={ButtonVariant.SECONDARY}
                            disabled={linkingCallInProgress}
                            onClick={onStopLinking}>
                            {t('tool-panel.switch.geometry.cancel')}
                        </Button>
                        <Button
                            size={ButtonSize.SMALL}
                            disabled={!canLink}
                            isProcessing={linkingCallInProgress}
                            qa-id="link-geometry-switch"
                            onClick={link}>
                            {t('tool-panel.switch.geometry.save-link')}
                        </Button>
                    </InfoboxButtons>
                    {layoutSwitch.stateCategory === 'NOT_EXISTING' && (
                        <InfoboxContentSpread>
                            <MessageBox type={MessageBoxType.ERROR}>
                                {t('tool-panel.switch.layout.cant-link-deleted')}
                            </MessageBox>
                        </InfoboxContentSpread>
                    )}
                </InfoboxContent>
            </Infobox>
        </React.Fragment>
    );
};
