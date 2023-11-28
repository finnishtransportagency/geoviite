import React from 'react';
import { useTranslation } from 'react-i18next';
import { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize } from 'vayla-design-lib/button/button';
import { LinkingState } from 'linking/linking-model';
import { WriteAccessRequired } from 'user/write-access-required';

type GeometrySwitchLinkingInitiationProps = {
    linkingState: LinkingState | undefined;
    hasSuggestedSwitch: boolean;
    onStartLinking: () => void;
};

export const GeometrySwitchLinkingInitiation: React.FC<GeometrySwitchLinkingInitiationProps> = ({
    linkingState,
    hasSuggestedSwitch,
    onStartLinking,
}) => {
    const { t } = useTranslation();
    return (
        <WriteAccessRequired>
            {linkingState === undefined &&
                (hasSuggestedSwitch ? (
                    <InfoboxButtons>
                        <Button
                            size={ButtonSize.SMALL}
                            onClick={onStartLinking}
                            qa-id={'start-linking'}>
                            {t('tool-panel.switch.geometry.start-setup')}
                        </Button>
                    </InfoboxButtons>
                ) : (
                    <InfoboxContentSpread>
                        <MessageBox>
                            {t(
                                'tool-panel.switch.geometry.cannot-start-switch-linking-related-tracks-not-linked-msg',
                            )}
                        </MessageBox>
                    </InfoboxContentSpread>
                ))}
        </WriteAccessRequired>
    );
};
