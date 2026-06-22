import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { formatDateShort } from 'utils/date-utils';
import Infobox from 'tool-panel/infobox/infobox';
import { useTrackNumberChangeTimes } from 'track-layout/track-layout-react-utils';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { createDelegates } from 'store/store-utils';
import { useAppNavigate } from 'common/navigate';
import { SearchItemType } from 'asset-search/search-dropdown';
import { useHasPublicationLog } from 'publication/publication-utils';
import { getTrackNumber } from 'track-layout/layout-track-number-api';

type TrackNumberChangeInfoInfoboxProps = {
    trackNumber: LayoutTrackNumber;
    layoutContext: LayoutContext;
    visible: boolean;
    visibilityChange: () => void;
};

export const TrackNumberChangeInfoInfobox: React.FC<TrackNumberChangeInfoInfoboxProps> = ({
    trackNumber,
    layoutContext,
    visible,
    visibilityChange,
}) => {
    const { t } = useTranslation();
    const trackNumberChangeInfo = useTrackNumberChangeTimes(trackNumber.id, layoutContext);
    const createdTime = trackNumberChangeInfo?.created;
    const changedTime = trackNumberChangeInfo?.changed;

    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const navigate = useAppNavigate();

    const hasPublicationLog = useHasPublicationLog(trackNumber.id, getTrackNumber, changedTime);
    const openPublicationLogButtonTitle = hasPublicationLog
        ? undefined
        : t('tool-panel.track-number.publication-log-unavailable');

    const openPublicationLog = React.useCallback(() => {
        delegates.startFreshSpecificItemPublicationLogSearch({
            type: SearchItemType.TRACK_NUMBER,
            trackNumber,
        });
        navigate('publication-search');
    }, [trackNumber, createdTime, changedTime]);

    return (
        trackNumberChangeInfo && (
            <Infobox
                contentVisible={visible}
                onContentVisibilityChange={visibilityChange}
                title={t('tool-panel.reference-line.change-info-heading')}
                qa-id="trackNumber-log-infobox">
                <InfoboxContent>
                    <InfoboxField
                        qaId="trackNumber-created-date"
                        label={t('tool-panel.created')}
                        value={createdTime === undefined ? '' : formatDateShort(createdTime)}
                    />
                    <InfoboxField
                        qaId="TrackNumber-changed-date"
                        label={t('tool-panel.changed')}
                        value={
                            changedTime === undefined
                                ? t('tool-panel.unmodified-in-geoviite')
                                : formatDateShort(changedTime)
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
