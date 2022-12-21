import { ChangeTimes } from 'track-layout/track-layout-store';
import { PublishRequest } from 'publication/publication-api';
import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';
import * as React from 'react';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import { PreviewSelectType } from 'preview/preview-table';
import {
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { ChangesBeingReverted } from 'preview/preview-view';
import {
    useLocationTrack,
    useReferenceLine,
    useSwitch,
    useTrackNumbers,
} from 'track-layout/track-layout-react-utils';
import { TimeStamp } from 'common/common-model';

export interface PreviewRejectConfirmDialogProps {
    changesBeingReverted: ChangesBeingReverted;
    changeTimes: ChangeTimes;
    confirmRevertChanges: () => void;
    cancelRevertChanges: () => void;
}

const typeTranslationKey = (type: PreviewSelectType) => {
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

const onlyDependencies = (changesBeingReverted: ChangesBeingReverted): PublishRequest => {
    const allChanges = changesBeingReverted.changeIncludingDependencies;
    const reqType = changesBeingReverted.requestedRevertChange.type;
    const reqId = changesBeingReverted.requestedRevertChange.id;
    return {
        trackNumbers: allChanges.trackNumbers.filter(
            (tn) => reqType != PreviewSelectType.trackNumber || tn !== reqId,
        ),
        kmPosts: allChanges.kmPosts,
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

const publicationRequestSize = (req: PublishRequest): number =>
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
            {t('publish.revert-confirm.track-number')} {trackNumber.number}
        </li>
    );
};

const ReferenceLineItem: React.FC<{
    referenceLineId: ReferenceLineId;
    changeTimes: ChangeTimes;
}> = (props) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbers('DRAFT', props.changeTimes.layoutTrackNumber);
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
            {t('publish.revert-confirm.reference-line')} {trackNumber.number}
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
            {t('publish.revert-confirm.location-track')} {locationTrack.name}
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
            {t('publish.revert-confirm.switch')} {switchObj.name}
        </li>
    );
};

export const PreviewConfirmRevertChangesDialog: React.FC<PreviewRejectConfirmDialogProps> = ({
    changesBeingReverted,
    changeTimes,
    cancelRevertChanges,
    confirmRevertChanges,
}) => {
    const { t } = useTranslation();
    const [isReverting, setIsReverting] = React.useState(false);
    const dependencies = onlyDependencies(changesBeingReverted);

    return (
        <Dialog
            title={t('publish.revert-confirm.title')}
            variant={DialogVariant.LIGHT}
            allowClose={false}
            className={dialogStyles['dialog--wide']}
            footerContent={
                <React.Fragment>
                    <Button
                        onClick={cancelRevertChanges}
                        disabled={isReverting}
                        variant={ButtonVariant.SECONDARY}>
                        {t('publish.revert-confirm.cancel')}
                    </Button>
                    <Button
                        icon={Icons.Delete}
                        disabled={isReverting}
                        isProcessing={isReverting}
                        variant={ButtonVariant.WARNING}
                        onClick={() => {
                            setIsReverting(true);
                            confirmRevertChanges();
                        }}>
                        {t('publish.revert-confirm.confirm')}
                    </Button>
                </React.Fragment>
            }>
            <div>{`${t('publish.revert-confirm.description')} ${t(
                `publish.revert-confirm.${typeTranslationKey(
                    changesBeingReverted.requestedRevertChange.type,
                )}`,
            )} ${changesBeingReverted.requestedRevertChange.name}?`}</div>
            {publicationRequestSize(dependencies) > 0 && (
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
            )}
        </Dialog>
    );
};
