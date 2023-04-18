import React from 'react';
import { useTranslation } from 'react-i18next';
import { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize } from 'vayla-design-lib/button/button';
import { LinkingState } from 'linking/linking-model';
import { useCommonDataAppSelector } from 'store/hooks';

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
    const userHasWriteRole = useCommonDataAppSelector((state) => state.userHasWriteRole);
    return (
        <React.Fragment>
            {linkingState === undefined &&
                userHasWriteRole &&
                (hasSuggestedSwitch ? (
                    <InfoboxButtons>
                        <Button size={ButtonSize.SMALL} onClick={onStartLinking}>
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
        </React.Fragment>
    );
};
