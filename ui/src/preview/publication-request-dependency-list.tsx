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
    trackNumber: PublicationCandidateReference & { type: DraftChangeType.TRACK_NUMBER };
    changeTime: TimeStamp;
}> = (props) => {
    const { t } = useTranslation();
    return (
        <li>
            {t('publish.revert-confirm.dependency-list.track-number')}{' '}
            {props.trackNumber.number ?? (
                <LookupTrackNumberItem
                    layoutContext={props.layoutContext}
                    trackNumberId={props.trackNumber.id}
                    changeTime={props.changeTime}
                />
            )}
        </li>
    );
};

const LookupTrackNumberItem: React.FC<{
    layoutContext: LayoutContext;
    trackNumberId: LayoutTrackNumberId;
    changeTime: TimeStamp;
}> = (props) =>
    useTrackNumbersIncludingDeleted(
        draftLayoutContext(props.layoutContext),
        props.changeTime,
    )?.find((tn) => tn.id === props.trackNumberId)?.number ?? '';

const ReferenceLineItem: React.FC<{
    layoutContext: LayoutContext;
    referenceLine: PublicationCandidateReference & { type: DraftChangeType.REFERENCE_LINE };
    changeTimes: ChangeTimes;
}> = (props) => {
    const { t } = useTranslation();
    return (
        <li>
            {t('publish.revert-confirm.dependency-list.reference-line')}{' '}
            {props.referenceLine.name ?? (
                <LookupReferenceLineItem
                    layoutContext={props.layoutContext}
                    referenceLineId={props.referenceLine.id}
                    changeTimes={props.changeTimes}
                />
            )}
        </li>
    );
};

const LookupReferenceLineItem: React.FC<{
    layoutContext: LayoutContext;
    referenceLineId: ReferenceLineId;
    changeTimes: ChangeTimes;
}> = (props) => {
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
    return trackNumber?.number ?? '';
};

const LocationTrackItem: React.FC<{
    layoutContext: LayoutContext;
    locationTrack: PublicationCandidateReference & { type: DraftChangeType.LOCATION_TRACK };
    changeTime: TimeStamp;
}> = (props) => {
    const { t } = useTranslation();
    return (
        <li>
            {t('publish.revert-confirm.dependency-list.location-track')}{' '}
            {props.locationTrack.name ?? (
                <LookupLocationTrackItem
                    layoutContext={props.layoutContext}
                    locationTrackId={props.locationTrack.id}
                    changeTime={props.changeTime}
                />
            )}
        </li>
    );
};
const LookupLocationTrackItem: React.FC<{
    layoutContext: LayoutContext;
    locationTrackId: LocationTrackId;
    changeTime: TimeStamp;
}> = (props) =>
    useLocationTrack(
        props.locationTrackId,
        draftLayoutContext(props.layoutContext),
        props.changeTime,
    )?.name;

const SwitchItem: React.FC<{
    layoutContext: LayoutContext;
    switchCandidate: PublicationCandidateReference & { type: DraftChangeType.SWITCH };
}> = ({ layoutContext, switchCandidate }) => {
    const { t } = useTranslation();
    return (
        <li>
            {t('publish.revert-confirm.dependency-list.switch')}{' '}
            {switchCandidate.name ?? (
                <LookupSwitchItem layoutContext={layoutContext} switchId={switchCandidate.id} />
            )}
        </li>
    );
};

const LookupSwitchItem: React.FC<{ layoutContext: LayoutContext; switchId: LayoutSwitchId }> = ({
    layoutContext,
    switchId,
}) => useSwitch(switchId, draftLayoutContext(layoutContext))?.name;

const KmPostItem: React.FC<{
    layoutContext: LayoutContext;
    kmPost: PublicationCandidateReference & { type: DraftChangeType.KM_POST };
}> = ({ layoutContext, kmPost }) => {
    const { t } = useTranslation();
    return (
        <li>
            {t('publish.revert-confirm.dependency-list.km-post')}{' '}
            {kmPost.kmNumber ?? (
                <LookupKmPostItem layoutContext={layoutContext} kmPostId={kmPost.id} />
            )}
        </li>
    );
};

const LookupKmPostItem: React.FC<{ layoutContext: LayoutContext; kmPostId: LayoutKmPostId }> = ({
    layoutContext,
    kmPostId,
}) => useKmPost(kmPostId, draftLayoutContext(layoutContext))?.kmNumber;

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
): React.JSX.Element => {
    const candidateType = candidate.type;
    const candidateComponentKey = `${candidateType}-${candidate.id}`;

    switch (candidateType) {
        case DraftChangeType.TRACK_NUMBER:
            return (
                <TrackNumberItem
                    layoutContext={layoutContext}
                    key={candidateComponentKey}
                    trackNumber={candidate}
                    changeTime={changeTimes.layoutTrackNumber}
                />
            );

        case DraftChangeType.LOCATION_TRACK:
            return (
                <LocationTrackItem
                    layoutContext={layoutContext}
                    key={candidateComponentKey}
                    locationTrack={candidate}
                    changeTime={changeTimes.layoutLocationTrack}
                />
            );

        case DraftChangeType.REFERENCE_LINE:
            return (
                <ReferenceLineItem
                    layoutContext={layoutContext}
                    key={candidateComponentKey}
                    referenceLine={candidate}
                    changeTimes={changeTimes}
                />
            );

        case DraftChangeType.SWITCH:
            return (
                <SwitchItem
                    layoutContext={layoutContext}
                    switchCandidate={candidate}
                    key={candidateComponentKey}
                />
            );

        case DraftChangeType.KM_POST:
            return (
                <KmPostItem
                    layoutContext={layoutContext}
                    kmPost={candidate}
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
