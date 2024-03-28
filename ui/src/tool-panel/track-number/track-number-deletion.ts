import { DraftChangeType } from 'publication/publication-model';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { RevertRequestType } from 'preview/preview-view-revert-request';
import { ChangesBeingReverted } from 'preview/preview-view';
import { getRevertRequestDependencies } from 'publication/publication-api';

export const onRequestDeleteTrackNumber = (
    trackNumber: LayoutTrackNumber,
    setChangesBeingReverted: (changes: ChangesBeingReverted) => void,
) => {
    getRevertRequestDependencies([
        {
            id: trackNumber.id,
            type: DraftChangeType.TRACK_NUMBER,
        },
    ]).then((changeIncludingDependencies) =>
        setChangesBeingReverted({
            requestedRevertChange: {
                type: RevertRequestType.CHANGES_WITH_DEPENDENCIES,
                source: {
                    type: DraftChangeType.TRACK_NUMBER,
                    name: trackNumber.number,
                    id: trackNumber.id,
                },
            },
            changeIncludingDependencies,
        }),
    );
};
