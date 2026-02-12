import * as React from 'react';
import styles from './linked-items-list.scss';
import { useTranslation } from 'react-i18next';

import { LayoutReferenceLine, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { TrackNumberBadge } from 'geoviite-design-lib/alignment/track-number-badge';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { useLoader } from 'utils/react-utils';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createEmptyItemCollections } from 'selection/selection-store';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { ChangeTimes } from 'common/common-slice';
import { getMaxTimestamp } from 'utils/date-utils';
import { LayoutContext } from 'common/common-model';

type ReferenceLineNamesProps = {
    linkedReferenceLines: LayoutReferenceLine[];
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

const ReferenceLineNames: React.FC<ReferenceLineNamesProps> = ({
    linkedReferenceLines,
    layoutContext,
    changeTimes,
}) => {
    const { t } = useTranslation();

    const alignmentChangeTime = getMaxTimestamp(
        changeTimes.layoutLocationTrack,
        changeTimes.layoutReferenceLine,
    );
    const trackNumbers = useLoader(
        () => getTrackNumbers(layoutContext, alignmentChangeTime),
        [alignmentChangeTime, linkedReferenceLines, layoutContext],
    );

    const referenceLineName =
        linkedReferenceLines.length > 1
            ? t('tool-panel.reference-line.reference-line-plural')
            : t('tool-panel.reference-line.reference-line-singular');

    const clickAction = createSelectAction();

    const referenceLineTrackNumberPair = new Map(
        linkedReferenceLines.map((a) => [a, getTrackNumber(a)]),
    );

    const sortedReferenceLineTrackNumberPairs = new Map(
        [...referenceLineTrackNumberPair.entries()].sort((a, b) => {
            if (a[1] && b[1]) {
                return a[1].localeCompare(b[1]);
            } else {
                return 0;
            }
        }),
    );

    const referenceLinesWithoutTrackNumbers = [...sortedReferenceLineTrackNumberPairs.keys()];

    function getTrackNumber(referenceLine: LayoutReferenceLine) {
        return trackNumbers?.find((t) => t.id === referenceLine.trackNumberId)?.number;
    }

    return (
        <InfoboxField
            label={referenceLineName}
            qaId="geometry-alignment-linked-reference-lines"
            value={
                <div className={styles['linked-items-list']}>
                    {referenceLinesWithoutTrackNumbers?.map((referenceLine) => {
                        const trackNumber = trackNumbers?.find(
                            (t) => t.id === referenceLine.trackNumberId,
                        );
                        return (
                            trackNumber && (
                                <div
                                    className={styles['linked-items-list__list-item']}
                                    key={referenceLine.id}>
                                    <TrackNumberBadge
                                        trackNumber={trackNumber}
                                        onClick={() => trackNumber && clickAction(trackNumber.id)}
                                    />
                                </div>
                            )
                        );
                    })}
                </div>
            }
        />
    );
};

export default ReferenceLineNames;
