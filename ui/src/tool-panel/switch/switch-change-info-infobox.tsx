import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { formatDateShort } from 'utils/date-utils';
import Infobox from 'tool-panel/infobox/infobox';
import { useSwitchChangeTimes } from 'track-layout/track-layout-react-utils';
import { LayoutSwitch } from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { createDelegates } from 'store/store-utils';
import { useAppNavigate } from 'common/navigate';
import { SearchItemType } from 'tool-bar/search-dropdown';

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

    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const navigate = useAppNavigate();

    const openPublicationLog = React.useCallback(() => {
        if (switchChangeInfo) {
            delegates.setSelectedPublicationSearchStartDate(switchChangeInfo.created);
            delegates.setSelectedPublicationSearchEndDate(switchChangeInfo.changed);
            delegates.setSelectedPublicationSearchSearchableItem({
                type: SearchItemType.SWITCH,
                layoutSwitch,
            });
            navigate('publication-search');
        }
    }, [layoutSwitch, switchChangeInfo]);

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
                            switchChangeInfo.changed && formatDateShort(switchChangeInfo.changed)
                        }
                    />
                    <div>
                        <Button
                            size={ButtonSize.SMALL}
                            variant={ButtonVariant.SECONDARY}
                            onClick={openPublicationLog}>
                            {t('tool-panel.show-in-publication-log')}
                        </Button>
                    </div>
                </InfoboxContent>
            </Infobox>
        )
    );
};
