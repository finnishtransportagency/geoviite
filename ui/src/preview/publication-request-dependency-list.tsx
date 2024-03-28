import { DraftChangeType, PublicationCandidateReference } from 'publication/publication-model';
import * as React from 'react';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { draftLayoutContext, LayoutContext, TimeStamp } from 'common/common-model';
import { useTranslation } from 'react-i18next';
import {
    useKmPost,
    useLocationTrack,
    useReferenceLine,
    useSwitch,
    useTrackNumbersIncludingDeleted,
} from 'track-layout/track-layout-react-utils';
import { ChangeTimes } from 'common/common-slice';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { RevertRequestType } from 'preview/preview-view-revert-request';
import { ChangesBeingReverted } from 'preview/preview-view';

const TrackNumberItem: React.FC<{
    layoutContext: LayoutContext;
    trackNumberId: LayoutTrackNumberId;
    changeTime: TimeStamp;
}> = (props) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbersIncludingDeleted(
        draftLayoutContext(props.layoutContext),
        props.changeTime,
    );
    const trackNumber = trackNumbers && trackNumbers.find((tn) => tn.id === props.trackNumberId);
    return trackNumber === undefined ? (
        <li />
    ) : (
        <li>
            {t('publish.revert-confirm.dependency-list.track-number')} {trackNumber.number}
        </li>
    );
};

const ReferenceLineItem: React.FC<{
    layoutContext: LayoutContext;
    referenceLineId: ReferenceLineId;
    changeTimes: ChangeTimes;
}> = (props) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbersIncludingDeleted(
        draftLayoutContext(props.layoutContext),
        props.changeTimes.layoutTrackNumber,
    );
    const referenceLine = useReferenceLine(
        props.referenceLineId,
        draftLayoutContext(props.layoutContext),
        props.changeTimes.layoutReferenceLine,
    );
    if (referenceLine === undefined || trackNumbers === undefined) {
        return <li />;
    }
    const trackNumber = trackNumbers.find((tn) => tn.id === referenceLine.trackNumberId);
    return trackNumber === undefined ? (
        <li />
    ) : (
        <li>
            {t('publish.revert-confirm.dependency-list.reference-line')} {trackNumber.number}
        </li>
    );
};

const LocationTrackItem: React.FC<{
    layoutContext: LayoutContext;
    locationTrackId: LocationTrackId;
    changeTime: TimeStamp;
}> = (props) => {
    const { t } = useTranslation();
    const locationTrack = useLocationTrack(
        props.locationTrackId,
        draftLayoutContext(props.layoutContext),
        props.changeTime,
    );
    return locationTrack === undefined ? (
        <li />
    ) : (
        <li>
            {t('publish.revert-confirm.dependency-list.location-track')} {locationTrack.name}
        </li>
    );
};

const SwitchItem: React.FC<{ layoutContext: LayoutContext; switchId: LayoutSwitchId }> = ({
    layoutContext,
    switchId,
}) => {
    const { t } = useTranslation();
    const switchObj = useSwitch(switchId, draftLayoutContext(layoutContext));
    return switchObj === undefined ? (
        <li />
    ) : (
        <li>
            {t('publish.revert-confirm.dependency-list.switch')} {switchObj.name}
        </li>
    );
};

const KmPostItem: React.FC<{ layoutContext: LayoutContext; kmPostId: LayoutKmPostId }> = ({
    layoutContext,
    kmPostId,
}) => {
    const { t } = useTranslation();
    const kmPost = useKmPost(kmPostId, draftLayoutContext(layoutContext));
    return kmPost === undefined ? (
        <li />
    ) : (
        <li>
            {t('publish.revert-confirm.dependency-list.km-post')} {kmPost.kmNumber}
        </li>
    );
};

export const publicationRequestTypeTranslationKey = (type: DraftChangeType) => {
    switch (type) {
        case DraftChangeType.TRACK_NUMBER:
            return 'track-number';
        case DraftChangeType.LOCATION_TRACK:
            return 'location-track';
        case DraftChangeType.REFERENCE_LINE:
            return 'reference-line';
        case DraftChangeType.SWITCH:
            return 'switch';
        case DraftChangeType.KM_POST:
            return 'km-post';

        default:
            return exhaustiveMatchingGuard(type);
    }
};

export const onlyDependencies = (
    changesBeingReverted: ChangesBeingReverted,
): PublicationCandidateReference[] => {
    const allChanges = changesBeingReverted.changeIncludingDependencies;
    const reqType = changesBeingReverted.requestedRevertChange.source.type;
    const reqId = changesBeingReverted.requestedRevertChange.source.id;

    return allChanges.filter((candidate) => candidate.id !== reqId && candidate.type !== reqType);
};

const filterDisplayedDependencies = (
    changesBeingReverted: ChangesBeingReverted,
): PublicationCandidateReference[] => {
    const revertRequestType = changesBeingReverted.requestedRevertChange.type;

    switch (revertRequestType) {
        case RevertRequestType.STAGE_CHANGES:
        case RevertRequestType.PUBLICATION_GROUP:
            return changesBeingReverted.changeIncludingDependencies;

        case RevertRequestType.CHANGES_WITH_DEPENDENCIES:
            return onlyDependencies(changesBeingReverted);

        default:
            return exhaustiveMatchingGuard(revertRequestType);
    }
};

const getPublicationCandidateComponent = (
    layoutContext: LayoutContext,
    candidate: PublicationCandidateReference,
    changeTimes: ChangeTimes,
): JSX.Element => {
    const candidateType = candidate.type;
    const candidateComponentKey = `${candidateType}-${candidate.id}`;

    switch (candidateType) {
        case DraftChangeType.TRACK_NUMBER:
            return (
                <TrackNumberItem
                    layoutContext={layoutContext}
                    key={candidateComponentKey}
                    trackNumberId={candidate.id}
                    changeTime={changeTimes.layoutTrackNumber}
                />
            );

        case DraftChangeType.LOCATION_TRACK:
            return (
                <LocationTrackItem
                    layoutContext={layoutContext}
                    key={candidateComponentKey}
                    locationTrackId={candidate.id}
                    changeTime={changeTimes.layoutLocationTrack}
                />
            );

        case DraftChangeType.REFERENCE_LINE:
            return (
                <ReferenceLineItem
                    layoutContext={layoutContext}
                    key={candidateComponentKey}
                    referenceLineId={candidate.id}
                    changeTimes={changeTimes}
                />
            );

        case DraftChangeType.SWITCH:
            return (
                <SwitchItem
                    layoutContext={layoutContext}
                    switchId={candidate.id}
                    key={candidateComponentKey}
                />
            );

        case DraftChangeType.KM_POST:
            return (
                <KmPostItem
                    layoutContext={layoutContext}
                    kmPostId={candidate.id}
                    key={candidateComponentKey}
                />
            );

        default:
            return exhaustiveMatchingGuard(candidateType);
    }
};

const sortCandidatesByAssetType = (
    candidates: PublicationCandidateReference[],
): PublicationCandidateReference[] => {
    const candidateDisplayOrder = {
        [DraftChangeType.TRACK_NUMBER]: 1,
        [DraftChangeType.REFERENCE_LINE]: 2,
        [DraftChangeType.LOCATION_TRACK]: 3,
        [DraftChangeType.SWITCH]: 4,
        [DraftChangeType.KM_POST]: 5,
    };

    return [...candidates].sort((a, b) => {
        return candidateDisplayOrder[a.type] - candidateDisplayOrder[b.type];
    });
};

export interface PublicationRequestDependencyListProps {
    layoutContext: LayoutContext;
    changesBeingReverted: ChangesBeingReverted;
    changeTimes: ChangeTimes;
}

export const PublicationRequestDependencyList: React.FC<PublicationRequestDependencyListProps> = ({
    layoutContext,
    changesBeingReverted,
    changeTimes,
}) => {
    const { t } = useTranslation();

    const dependencies = filterDisplayedDependencies(changesBeingReverted);
    const sortedDependencies = sortCandidatesByAssetType(dependencies);

    const displayExtraDependencyInformation =
        changesBeingReverted.requestedRevertChange.type ===
        RevertRequestType.CHANGES_WITH_DEPENDENCIES;

    return (
        sortedDependencies.length > 0 && (
            <>
                <div>
                    {displayExtraDependencyInformation && t(`publish.revert-confirm.dependencies`)}
                </div>
                <ul>
                    {sortedDependencies.map((candidate) =>
                        getPublicationCandidateComponent(layoutContext, candidate, changeTimes),
                    )}
                </ul>
            </>
        )
    );
};
