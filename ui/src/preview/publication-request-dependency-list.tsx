import { PublishRequestIds } from 'publication/publication-model';
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
import { PreviewSelectType } from 'preview/preview-table';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { RevertRequestType } from 'preview/preview-view-revert-request';
import { ChangesBeingReverted } from 'preview/preview-view';

const publicationRequestSize = (req: PublishRequestIds): number =>
    req.switches.length +
    req.trackNumbers.length +
    req.locationTracks.length +
    req.referenceLines.length +
    req.kmPosts.length;

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

export const publicationRequestTypeTranslationKey = (type: PreviewSelectType) => {
    switch (type) {
        case 'trackNumber':
            return 'track-number';
        case 'referenceLine':
            return 'reference-line';
        case 'locationTrack':
            return 'location-track';
        case 'switch':
            return 'switch';
        case 'kmPost':
            return 'km-post';
    }
};

export const onlyDependencies = (changesBeingReverted: ChangesBeingReverted): PublishRequestIds => {
    const allChanges = changesBeingReverted.changeIncludingDependencies;
    const reqType = changesBeingReverted.requestedRevertChange.source.type;
    const reqId = changesBeingReverted.requestedRevertChange.source.id;
    return {
        trackNumbers: allChanges.trackNumbers.filter(
            (tn) => reqType != PreviewSelectType.trackNumber || tn !== reqId,
        ),
        kmPosts: allChanges.kmPosts.filter(
            (kmPost) => reqType != PreviewSelectType.kmPost || kmPost !== reqId,
        ),
        referenceLines: allChanges.referenceLines.filter(
            (rl) => reqType != PreviewSelectType.referenceLine || rl !== reqId,
        ),
        switches: allChanges.switches.filter(
            (sw) => reqType != PreviewSelectType.switch || sw !== reqId,
        ),
        locationTracks: allChanges.locationTracks.filter(
            (lt) => reqType != PreviewSelectType.locationTrack || lt !== reqId,
        ),
    };
};

const filterDisplayedDependencies = (
    changesBeingReverted: ChangesBeingReverted,
): PublishRequestIds => {
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

    const displayExtraDependencyInformation =
        changesBeingReverted.requestedRevertChange.type ===
        RevertRequestType.CHANGES_WITH_DEPENDENCIES;

    return (
        publicationRequestSize(dependencies) > 0 && (
            <>
                <div>
                    {displayExtraDependencyInformation && t(`publish.revert-confirm.dependencies`)}
                </div>
                <ul>
                    {dependencies.trackNumbers.map((tn) => (
                        <TrackNumberItem
                            layoutContext={layoutContext}
                            trackNumberId={tn}
                            changeTime={changeTimes.layoutTrackNumber}
                            key={tn}
                        />
                    ))}

                    {dependencies.referenceLines.map((rl) => (
                        <ReferenceLineItem
                            layoutContext={layoutContext}
                            referenceLineId={rl}
                            changeTimes={changeTimes}
                            key={rl}
                        />
                    ))}
                    {dependencies.locationTracks.map((lt) => (
                        <LocationTrackItem
                            layoutContext={layoutContext}
                            locationTrackId={lt}
                            changeTime={changeTimes.layoutLocationTrack}
                            key={lt}
                        />
                    ))}
                    {dependencies.switches.map((sw) => (
                        <SwitchItem layoutContext={layoutContext} switchId={sw} key={sw} />
                    ))}
                    {dependencies.kmPosts.map((kmPost) => (
                        <KmPostItem layoutContext={layoutContext} kmPostId={kmPost} key={kmPost} />
                    ))}
                </ul>
            </>
        )
    );
};
