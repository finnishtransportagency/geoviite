import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { formatDateShort } from 'utils/date-utils';
import Infobox from 'tool-panel/infobox/infobox';
import { useSwitchChangeTimes } from 'track-layout/track-layout-react-utils';
import { LayoutSwitch } from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { useNavigate } from 'react-router-dom';
import { SearchItemType } from 'asset-search/search-dropdown';
import { getSwitch } from 'track-layout/layout-switch-api';
import { useHasPublicationLog } from 'publication/publication-utils';
import { publicationLogUrlForItem } from 'publication/log/publication-log-params';

type SwitchChangeInfoInfoboxProps = {
    layoutSwitch: LayoutSwitch;
    layoutContext: LayoutContext;
    visible: boolean;
    visibilityChange: () => void;
};

export const SwitchChangeInfoInfobox: React.FC<SwitchChangeInfoInfoboxProps> = ({
    layoutSwitch,
    layoutContext,
    visible,
    visibilityChange,
}) => {
    const { t } = useTranslation();
    const switchId = layoutSwitch.id;
    const switchChangeInfo = useSwitchChangeTimes(switchId, layoutContext);

    const navigate = useNavigate();

    const hasPublicationLog = useHasPublicationLog(switchId, getSwitch, switchChangeInfo?.changed);
    const openPublicationLogButtonTitle = hasPublicationLog
        ? undefined
        : t('tool-panel.switch.layout.publication-log-unavailable');

    const openPublicationLog = React.useCallback(() => {
        navigate(publicationLogUrlForItem({ type: SearchItemType.SWITCH, layoutSwitch }));
    }, [layoutSwitch, navigate]);

    return (
        switchChangeInfo && (
            <Infobox
                contentVisible={visible}
                onContentVisibilityChange={visibilityChange}
                title={t('tool-panel.switch.layout.change-info-heading')}
                qa-id="switch-log-infobox">
                <InfoboxContent>
                    <InfoboxField
                        qaId="switch-created-date"
                        label={t('tool-panel.created')}
                        value={formatDateShort(switchChangeInfo.created)}
                    />
                    <InfoboxField
                        qaId="switch-changed-date"
                        label={t('tool-panel.changed')}
                        value={
                            switchChangeInfo.changed
                                ? formatDateShort(switchChangeInfo.changed)
                                : t('tool-panel.unmodified-in-geoviite')
                        }
                    />
                    <div>
                        <Button
                            size={ButtonSize.SMALL}
                            variant={ButtonVariant.SECONDARY}
                            title={openPublicationLogButtonTitle}
                            disabled={!hasPublicationLog}
                            onClick={openPublicationLog}>
                            {t('tool-panel.show-in-publication-log')}
                        </Button>
                    </div>
                </InfoboxContent>
            </Infobox>
        )
    );
};
