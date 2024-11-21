import React from 'react';
import { useTranslation } from 'react-i18next';
import { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize } from 'vayla-design-lib/button/button';
import { GeometrySwitchSuggestionFailureReason, LinkingState } from 'linking/linking-model';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_LAYOUT } from 'user/user-model';
import { LayoutContext } from 'common/common-model';

type GeometrySwitchLinkingInitiationProps = {
    linkingState: LinkingState | undefined;
    hasSuggestedSwitch: boolean;
    geometrySwitchInvalidityReason: GeometrySwitchSuggestionFailureReason | undefined;
    onStartLinking: () => void;
    layoutContext: LayoutContext;
};

export const GeometrySwitchLinkingInitiation: React.FC<GeometrySwitchLinkingInitiationProps> = ({
    linkingState,
    hasSuggestedSwitch,
    onStartLinking,
    geometrySwitchInvalidityReason,
    layoutContext,
}) => {
    const { t } = useTranslation();
    return (
        <PrivilegeRequired privilege={EDIT_LAYOUT}>
            {linkingState === undefined &&
                (geometrySwitchInvalidityReason !== undefined ? (
                    <InfoboxContentSpread>
                        <MessageBox>
                            {t(
                                `enum.GeometrySwitchSuggestionFailureReason.${geometrySwitchInvalidityReason}`,
                            )}
                        </MessageBox>
                    </InfoboxContentSpread>
                ) : (
                    hasSuggestedSwitch && (
                        <InfoboxButtons>
                            <Button
                                disabled={layoutContext.publicationState !== 'DRAFT'}
                                size={ButtonSize.SMALL}
                                qa-id="start-geometry-switch-linking"
                                onClick={onStartLinking}>
                                {t('tool-panel.switch.geometry.start-setup')}
                            </Button>
                        </InfoboxButtons>
                    )
                ))}
        </PrivilegeRequired>
    );
};
