import * as React from 'react';
import styles from './linked-items-list.scss';
import { useTranslation } from 'react-i18next';

import {
    LayoutReferenceLine,
    LayoutTrackNumberId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { ReferenceLineBadge } from 'geoviite-design-lib/alignment/reference-line-badge';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { PublishType, TimeStamp } from 'common/common-model';
import { useLoader } from 'utils/react-utils';
import { useTrackLayoutAppDispatch } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-store';
import { createEmptyItemCollections } from 'selection/selection-store';
import InfoboxField from 'tool-panel/infobox/infobox-field';

type ReferenceLineNamesProps = {
    linkedReferenceLines: LayoutReferenceLine[];
    publishType: PublishType;
    alignmentChangeTime: TimeStamp;
};

function createSelectAction() {
    const dispatch = useTrackLayoutAppDispatch();
    const delegates = createDelegates(dispatch, TrackLayoutActions);
    return (trackNumberId: LayoutTrackNumberId, referenceLineId: ReferenceLineId | undefined) =>
        delegates.onSelect({
            ...createEmptyItemCollections(),
            trackNumbers: [trackNumberId],
            referenceLines: referenceLineId ? [referenceLineId] : [],
        });
}

const ReferenceLineNames: React.FC<ReferenceLineNamesProps> = ({
    linkedReferenceLines,
    publishType,
    alignmentChangeTime,
}) => {
    const { t } = useTranslation();
    const trackNumbers = useLoader(
        () => getTrackNumbers(publishType, alignmentChangeTime),
        [alignmentChangeTime, linkedReferenceLines, publishType],
    );

    const referenceLineName =
        linkedReferenceLines && linkedReferenceLines.length > 1
            ? t('tool-panel.reference-line.reference-line-plural')
            : t('tool-panel.reference-line.reference-line-singular');

    const clickAction = createSelectAction();

    const referenceLineTrackNumberPair = new Map(
        linkedReferenceLines?.map((a) => [a, getTrackNumber(a)]),
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
            value={
                <React.Fragment>
                    {referenceLinesWithoutTrackNumbers?.map((referenceLine) => {
                        const trackNumber = trackNumbers?.find(
                            (t) => t.id === referenceLine.trackNumberId,
                        );
                        return (
                            trackNumber && (
                                <div
                                    className={styles['linked-items-list__list-item']}
                                    key={referenceLine.id}>
                                    <ReferenceLineBadge
                                        trackNumber={trackNumber}
                                        onClick={() =>
                                            trackNumber &&
                                            clickAction(trackNumber.id, referenceLine.id)
                                        }
                                    />
                                </div>
                            )
                        );
                    })}
                </React.Fragment>
            }
        />
    );
};

export default ReferenceLineNames;
