import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { formatDateShort, getMaxTimestamp, getMinTimestamp } from 'utils/date-utils';
import Infobox from 'tool-panel/infobox/infobox';
import {
    useReferenceLineChangeTimes,
    useTrackNumberChangeTimes,
} from 'track-layout/track-layout-react-utils';
import { LayoutReferenceLine, LayoutTrackNumber } from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { createDelegates } from 'store/store-utils';
import { useAppNavigate } from 'common/navigate';
import { SearchItemType } from 'asset-search/search-dropdown';

type TrackNumberChangeInfoInfoboxProps = {
    trackNumber: LayoutTrackNumber;
    referenceLine: LayoutReferenceLine | undefined;
    layoutContext: LayoutContext;
    visible: boolean;
    visibilityChange: () => void;
};

export const TrackNumberChangeInfoInfobox: React.FC<TrackNumberChangeInfoInfoboxProps> = ({
    trackNumber,
    referenceLine,
    layoutContext,
    visible,
    visibilityChange,
}) => {
    const { t } = useTranslation();
    const trackNumberChangeInfo = useTrackNumberChangeTimes(trackNumber.id, layoutContext);
    const tnChangeTimes = useTrackNumberChangeTimes(trackNumber?.id, layoutContext);
    const rlChangeTimes = useReferenceLineChangeTimes(referenceLine?.id, layoutContext);
    const createdTime =
        tnChangeTimes?.created && rlChangeTimes?.created
            ? getMinTimestamp(tnChangeTimes.created, rlChangeTimes.created)
            : tnChangeTimes?.created || rlChangeTimes?.created;
    const changedTime =
        tnChangeTimes?.changed && rlChangeTimes?.changed
            ? getMaxTimestamp(tnChangeTimes.changed, rlChangeTimes.changed)
            : tnChangeTimes?.changed || rlChangeTimes?.changed;

    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const navigate = useAppNavigate();

    const openPublicationLog = React.useCallback(() => {
        if (createdTime) {
            delegates.setSelectedPublicationSearchStartDate(createdTime);
        }
        if (changedTime) {
            delegates.setSelectedPublicationSearchEndDate(changedTime);
        }
        delegates.setSelectedPublicationSearchSearchableItem({
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
                            onClick={openPublicationLog}>
                            {t('tool-panel.show-in-publication-log')}
                        </Button>
                    </div>
                </InfoboxContent>
            </Infobox>
        )
    );
};
