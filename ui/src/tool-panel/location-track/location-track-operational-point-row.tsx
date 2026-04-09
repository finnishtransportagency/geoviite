import React from 'react';
import { useTranslation } from 'react-i18next';
import { LayoutLocationTrack, OperationalPoint } from 'track-layout/track-layout-model';
import { LayoutContext, TrackMeter } from 'common/common-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import styles from './location-track-operational-point-links-infobox.scss';
import NavigableTrackMeter from 'geoviite-design-lib/track-meter/navigable-track-meter';
import { OperationalPointBadge } from 'geoviite-design-lib/operational-point/operational-point-badge';
import { OnSelectOptions } from 'selection/selection-model';
import infoboxStyles from 'tool-panel/infobox/infobox.module.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { LocationTrackDetachOperationalPointDialog } from './dialog/location-track-detach-operational-point-dialog';

type LocationTrackOperationalPointRowProps = {
    operationalPoint: OperationalPoint;
    address: TrackMeter | undefined;
    layoutContext: LayoutContext;
    locationTrack: LayoutLocationTrack;
    onSelect: (items: OnSelectOptions) => void;
};

export const LocationTrackOperationalPointRow: React.FC<
    LocationTrackOperationalPointRowProps
> = ({ operationalPoint, address, layoutContext, locationTrack, onSelect }) => {
    const { t } = useTranslation();
    const [showDetachDialog, setShowDetachDialog] = React.useState(false);
    const remarkClassNames = createClassName(
        styles['location-track-operational-point-links-infobox-list__remark'],
        infoboxStyles['infobox__list-cell--strong'],
    );

    return (
        <>
            <div>
                <OperationalPointBadge
                    operationalPoint={operationalPoint}
                    onClick={() =>
                        onSelect({
                            operationalPoints: [operationalPoint.id],
                            selectedTab: { id: operationalPoint.id, type: 'OPERATIONAL_POINT' },
                        })
                    }
                />
            </div>
            <div className={infoboxStyles['infobox__list-cell--strong']}>
                {!address ? (
                    t('tool-panel.location-track.operational-point-links.no-location')
                ) : (
                    <NavigableTrackMeter
                        trackMeter={address}
                        displayDecimals={false}
                        location={operationalPoint.location}
                    />
                )}
            </div>
            <div className={remarkClassNames}>
                {operationalPoint.state === 'DELETED' &&
                    t('tool-panel.location-track.operational-point-links.not-existing')}
            </div>
            <div>
                {layoutContext.publicationState === 'DRAFT' && (
                    <Button
                        size={ButtonSize.SMALL}
                        variant={ButtonVariant.GHOST}
                        onClick={() => setShowDetachDialog(true)}>
                        {t('tool-panel.location-track.operational-point-links.detach')}
                    </Button>
                )}
            </div>
            {showDetachDialog && (
                <LocationTrackDetachOperationalPointDialog
                    layoutContext={layoutContext}
                    locationTrackId={locationTrack.id}
                    locationTrackName={locationTrack.name}
                    operationalPointId={operationalPoint.id}
                    operationalPointName={operationalPoint.name}
                    onClose={() => setShowDetachDialog(false)}
                    onDetached={() => setShowDetachDialog(false)}
                />
            )}
        </>
    );
};
