import { PublishRequestIds } from 'publication/publication-model';
import * as React from 'react';
import {
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { TimeStamp } from 'common/common-model';
import { useTranslation } from 'react-i18next';
import {
    useLocationTrack,
    useReferenceLine,
    useSwitch,
    useTrackNumbers,
    useTrackNumbersIncludingDeleted,
} from 'track-layout/track-layout-react-utils';
import { ChangeTimes } from 'common/common-slice';
import { PreviewSelectType } from 'preview/preview-table';
import { ChangesBeingReverted } from 'preview/preview-view';

const publicationRequestSize = (req: PublishRequestIds): number =>
    req.switches.length +
    req.trackNumbers.length +
    req.locationTracks.length +
    req.referenceLines.length +
    req.kmPosts.length;

const TrackNumberItem: React.FC<{ trackNumberId: LayoutTrackNumberId; changeTime: TimeStamp }> = (
    props,
) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbers('DRAFT', props.changeTime);
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
    referenceLineId: ReferenceLineId;
    changeTimes: ChangeTimes;
}> = (props) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbersIncludingDeleted(
        'DRAFT',
        props.changeTimes.layoutTrackNumber,
    );
    const referenceLine = useReferenceLine(
        props.referenceLineId,
        'DRAFT',
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

const LocationTrackItem: React.FC<{ locationTrackId: LocationTrackId; changeTime: TimeStamp }> = (
    props,
) => {
    const { t } = useTranslation();
    const locationTrack = useLocationTrack(props.locationTrackId, 'DRAFT', props.changeTime);
    return locationTrack === undefined ? (
        <li />
    ) : (
        <li>
            {t('publish.revert-confirm.dependency-list.location-track')} {locationTrack.name}
        </li>
    );
};

const SwitchItem: React.FC<{ switchId: LayoutSwitchId }> = ({ switchId }) => {
    const { t } = useTranslation();
    const switchObj = useSwitch(switchId, 'DRAFT');
    return switchObj === undefined ? (
        <li />
    ) : (
        <li>
            {t('publish.revert-confirm.dependency-list.switch')} {switchObj.name}
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
    const reqType = changesBeingReverted.requestedRevertChange.type;
    const reqId = changesBeingReverted.requestedRevertChange.id;
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

export interface PublicationRequestDependencyListProps {
    dependencies: PublishRequestIds;
    changeTimes: ChangeTimes;
}

export const PublicationRequestDependencyList: React.FC<PublicationRequestDependencyListProps> = ({
    dependencies,
    changeTimes,
}) => {
    const { t } = useTranslation();
    return (
        publicationRequestSize(dependencies) > 0 && (
            <>
                <div>{t(`publish.revert-confirm.dependencies`)}</div>
                <ul>
                    {dependencies.trackNumbers.map((tn) => (
                        <TrackNumberItem
                            trackNumberId={tn}
                            changeTime={changeTimes.layoutTrackNumber}
                            key={tn}
                        />
                    ))}

                    {dependencies.referenceLines.map((rl) => (
                        <ReferenceLineItem
                            referenceLineId={rl}
                            changeTimes={changeTimes}
                            key={rl}
                        />
                    ))}
                    {dependencies.locationTracks.map((lt) => (
                        <LocationTrackItem
                            locationTrackId={lt}
                            changeTime={changeTimes.layoutLocationTrack}
                            key={lt}
                        />
                    ))}
                    {dependencies.switches.map((sw) => (
                        <SwitchItem switchId={sw} key={sw} />
                    ))}
                </ul>
            </>
        )
    );
};
