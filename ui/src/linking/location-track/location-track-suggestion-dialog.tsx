import React from 'react';
import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import { useTranslation } from 'react-i18next';
import { LocationTrackEndpoint } from 'linking/linking-model';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useLoader } from 'utils/react-utils';
import { LocationTrackPointUpdateType } from 'common/common-model';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { boundingBoxAroundPoints, expandBoundingBox } from 'model/geometry';
import { getSuggestedContinuousLocationTracks } from 'linking/linking-api';

export type ConnectedLocationTrackProps = {
    missingEndpoint: LocationTrackEndpoint;
    onSelectSuggestedLocationTrack: (
        locationTrackId: LocationTrackId,
        connectedLocationTrackId: LocationTrackId,
        locationTrackPointUpdateType: LocationTrackPointUpdateType,
    ) => void;
    onClose: () => void;
};

export const ConnectedLocationTrackDialog: React.FC<ConnectedLocationTrackProps> = ({
    missingEndpoint,
    onSelectSuggestedLocationTrack,
    onClose,
}) => {
    const { t } = useTranslation();
    const [continuousLocationTrackId, setContinuousLocationTrackId] = React.useState<
        LocationTrackId | undefined
    >(undefined);
    const locationTracks = useLoader(() => {
        const bbox = expandBoundingBox(boundingBoxAroundPoints([missingEndpoint.location]), 100);
        return getSuggestedContinuousLocationTracks(
            missingEndpoint.locationTrackId,
            missingEndpoint,
            bbox,
        );
    }, []);

    function selectLocationTrack() {
        if (continuousLocationTrackId) {
            onSelectSuggestedLocationTrack(
                missingEndpoint.locationTrackId,
                continuousLocationTrackId,
                missingEndpoint.updateType,
            );
        }
    }

    return (
        <Dialog
            title={t('continuous-track-suggestion-dialog-title')}
            onClose={onClose}
            variant={DialogVariant.DARK}
            style={{ width: '380px', minWidth: '380px' }}
            scrollable={false}
            footerContent={
                <React.Fragment>
                    <Button onClick={onClose} variant={ButtonVariant.SECONDARY}>
                        {t('button.cancel')}
                    </Button>
                    <Button disabled={!continuousLocationTrackId} onClick={selectLocationTrack}>
                        {t('button.continue')}
                    </Button>
                </React.Fragment>
            }>
            {locationTracks && locationTracks.length > 0 ? (
                <Dropdown
                    placeholder={
                        continuousLocationTrackId
                            ? continuousLocationTrackId
                            : t('continuous-track-suggestion-dialog-title')
                    }
                    value={continuousLocationTrackId}
                    options={locationTracks?.map((locationTrack) => ({
                        name: locationTrack.name,
                        value: locationTrack.id,
                    }))}
                    onChange={(locationTrackId) => setContinuousLocationTrackId(locationTrackId)}
                />
            ) : (
                <div>{t('continuous-track-suggestion-no-track-found')}</div>
            )}
        </Dialog>
    );
};
