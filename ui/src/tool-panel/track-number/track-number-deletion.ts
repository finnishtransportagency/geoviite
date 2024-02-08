import { getRevertRequestDependencies } from 'publication/publication-api';
import { publishNothing } from 'publication/publication-model';
import { PreviewSelectType } from 'preview/preview-table';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { ChangesBeingReverted } from 'preview/preview-view';

export const onRequestDeleteTrackNumber = (
    trackNumber: LayoutTrackNumber,
    setChangesBeingReverted: (changes: ChangesBeingReverted) => void,
) => {
    getRevertRequestDependencies({ ...publishNothing, trackNumbers: [trackNumber.id] }).then(
        (changeIncludingDependencies) =>
            setChangesBeingReverted({
                requestedRevertChange: {
                    type: PreviewSelectType.trackNumber,
                    name: trackNumber.number,
                    id: trackNumber.id,
                },
                changeIncludingDependencies,
            }),
    );
};
