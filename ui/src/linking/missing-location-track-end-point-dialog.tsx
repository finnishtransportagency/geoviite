import React from 'react';
import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import { LayoutEndPoint, LocationTrackPointUpdateType } from 'common/common-model';
import { Radio } from 'vayla-design-lib/radio/radio';
import { useTranslation } from 'react-i18next';
import { Button } from 'vayla-design-lib/button/button';
import LocationTrackEndPointLabel from 'geoviite-design-lib/alignment/location-track-end-point-label';
import { LocationTrackEndpoint } from 'linking/linking-model';
import { LayoutLocationTrack } from 'track-layout/track-layout-model';

export type MissingLocationTrackEndPointDialogProps = {
    missingEndpoint?: LocationTrackEndpoint;
    locationTrack?: LayoutLocationTrack;
    onSelect: (endPointType: LayoutEndPoint) => void;
    onClose: () => void;
};

export const MissingLocationTrackEndPointDialog: React.FC<
    MissingLocationTrackEndPointDialogProps
> = ({ missingEndpoint, locationTrack, onSelect, onClose }) => {
    const { t } = useTranslation();
    const [selectedEndpointType, setSelectedEndpointType] = React.useState<LayoutEndPoint>();
    const canCommitSelection = selectedEndpointType != null;

    function selectEndpointType(t: LayoutEndPoint) {
        setSelectedEndpointType(t);
    }

    function commitSelection() {
        if (canCommitSelection && selectedEndpointType) {
            onSelect(selectedEndpointType);
            onClose();
        }
    }

    function getFooterText(missingEndpoint: LocationTrackEndpoint) {
        return missingEndpoint.updateType == LocationTrackPointUpdateType.END_POINT
            ? t(`switch-suggestion-creator-dialog.location-track-footer-end-point`)
            : t(`switch-suggestion-creator-dialog.location-track-footer-start-point`);
    }

    const locationTrackName = locationTrack ? locationTrack.name : '';

    return (
        <Dialog
            title={t(`missing-location-track-end-point-dialog.title`) + locationTrackName}
            onClose={onClose}
            variant={DialogVariant.DARK}
            style={{ minWidth: '300px' }}
            footerContent={
                <React.Fragment>
                    <Button disabled={!canCommitSelection} onClick={commitSelection}>
                        {t('button.ok')}
                    </Button>
                </React.Fragment>
            }>
            <div style={{ display: 'grid', gridGap: '8px', marginBottom: '20px' }}>
                {Object.values(LayoutEndPoint).map((endpointType) => (
                    <Radio
                        key={endpointType}
                        checked={endpointType == selectedEndpointType}
                        onChange={(e) => e.target.checked && selectEndpointType(endpointType)}>
                        <LocationTrackEndPointLabel endPointType={endpointType} />
                    </Radio>
                ))}
            </div>
            {missingEndpoint && getFooterText(missingEndpoint)}
        </Dialog>
    );
};
