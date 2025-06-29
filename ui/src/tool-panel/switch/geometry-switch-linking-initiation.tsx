import React from 'react';
import { useTranslation } from 'react-i18next';
import { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize } from 'vayla-design-lib/button/button';
import {
    GeometrySwitchSuggestionFailureReason,
    LinkingState,
    SuggestedSwitch,
} from 'linking/linking-model';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_LAYOUT } from 'user/user-model';
import { LayoutContext } from 'common/common-model';

type GeometrySwitchLinkingInitiationProps = {
    linkingState: LinkingState | undefined;
    initialSuggestedSwitch: SuggestedSwitch | undefined;
    geometrySwitchInvalidityReason: GeometrySwitchSuggestionFailureReason | undefined;
    onStartLinking: (suggestedSwitch: SuggestedSwitch) => void;
    layoutContext: LayoutContext;
};

export const GeometrySwitchLinkingInitiation: React.FC<GeometrySwitchLinkingInitiationProps> = ({
    linkingState,
    initialSuggestedSwitch,
    onStartLinking,
    geometrySwitchInvalidityReason,
    layoutContext,
}) => {
    const { t } = useTranslation();

    const warningVisible =
        linkingState === undefined && geometrySwitchInvalidityReason !== undefined;

    const disabledReason =
        layoutContext.publicationState === 'OFFICIAL' && !warningVisible
            ? t('tool-panel.disabled.activity-disabled-in-official-mode')
            : '';

    return (
        <PrivilegeRequired privilege={EDIT_LAYOUT}>
            {warningVisible && (
                <InfoboxContentSpread>
                    <MessageBox>
                        {t(
                            `enum.GeometrySwitchSuggestionFailureReason.${geometrySwitchInvalidityReason}`,
                        )}
                    </MessageBox>
                </InfoboxContentSpread>
            )}
            <InfoboxButtons>
                <Button
                    disabled={
                        layoutContext.publicationState !== 'DRAFT' ||
                        initialSuggestedSwitch === undefined
                    }
                    size={ButtonSize.SMALL}
                    qa-id="start-geometry-switch-linking"
                    title={disabledReason}
                    onClick={() =>
                        initialSuggestedSwitch && onStartLinking(initialSuggestedSwitch)
                    }>
                    {t('tool-panel.switch.geometry.start-setup')}
                </Button>
            </InfoboxButtons>
        </PrivilegeRequired>
    );
};
