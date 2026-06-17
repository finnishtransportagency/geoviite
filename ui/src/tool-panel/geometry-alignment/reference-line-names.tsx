import * as React from 'react';
import styles from './linked-items-list.scss';
import { useTranslation } from 'react-i18next';
import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { TrackNumberBadge } from 'geoviite-design-lib/alignment/track-number-badge';
import { getSomeTrackNumbers } from 'track-layout/layout-track-number-api';
import { useLoader } from 'utils/react-utils';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createEmptyItemCollections } from 'selection/selection-store';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { ChangeTimes } from 'common/common-slice';
import { LayoutContext } from 'common/common-model';

type TrackNumberNamesProps = {
    linkedTrackNumberIds: LayoutTrackNumberId[];
    layoutContext: LayoutContext;
    changeTimes: ChangeTimes;
};

function createSelectAction() {
    const delegates = createDelegates(TrackLayoutActions);
    return (trackNumberId: LayoutTrackNumberId) =>
        delegates.onSelect({
            ...createEmptyItemCollections(),
            trackNumbers: [trackNumberId],
        });
}

const TrackNumberNames: React.FC<TrackNumberNamesProps> = ({
    linkedTrackNumberIds,
    layoutContext,
    changeTimes,
}) => {
    const { t } = useTranslation();

    const trackNumbers = useLoader(
        () =>
            getSomeTrackNumbers(
                linkedTrackNumberIds,
                layoutContext,
                changeTimes.layoutTrackNumber,
            ).then((tns) =>
                tns.sort((a, b) => {
                    return a.number.localeCompare(b.number);
                }),
            ),
        [changeTimes.layoutTrackNumber, linkedTrackNumberIds, layoutContext],
    );

    const referenceLineName =
        linkedTrackNumberIds.length > 1
            ? t('tool-panel.reference-line.reference-line-plural')
            : t('tool-panel.reference-line.reference-line-singular');

    const clickAction = createSelectAction();

    return (
        <InfoboxField
            label={referenceLineName}
            qaId="geometry-alignment-linked-reference-lines"
            value={
                <div className={styles['linked-items-list']}>
                    {trackNumbers?.map((tn) => {
                        return (
                            <div className={styles['linked-items-list__list-item']} key={tn.id}>
                                <TrackNumberBadge
                                    trackNumber={tn}
                                    onClick={() => clickAction(tn.id)}
                                />
                            </div>
                        );
                    })}
                </div>
            }
        />
    );
};

export default TrackNumberNames;
