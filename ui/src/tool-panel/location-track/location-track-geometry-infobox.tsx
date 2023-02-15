import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { useLoader } from 'utils/react-utils';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import { formatTrackMeter, formatTrackMeterWithoutMeters } from 'utils/geography-utils';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { getGeometries } from 'track-layout/layout-location-track-api';
import styles from './location-track-infobox.scss';
import { useAppNavigate } from 'common/navigate';
import { GeometryPlanId } from 'geometry/geometry-model';
import { Link } from 'vayla-design-lib/link/link';
import { PublishType } from 'common/common-model';
import { MapViewport } from 'map/map-model';

type LocationTrackGeometryInfoboxProps = {
    publishType: PublishType;
    locationTrackId: LocationTrackId;
    viewport: MapViewport;
};

export const LocationTrackGeometryInfobox: React.FC<LocationTrackGeometryInfoboxProps> = ({
    publishType,
    locationTrackId,
    viewport,
}) => {
    const [useBoungingBox, setUseBoundingBox] = React.useState(true);
    const elements = useLoader(
        () =>
            getGeometries(
                publishType,
                locationTrackId,
                useBoungingBox ? viewport.area : undefined,
            ).then((geom) => geom),
        [locationTrackId, useBoungingBox, viewport.area],
    );
    const navigate = useAppNavigate();

    const onSelectPlan = (planId: GeometryPlanId) => navigate('inframodel-edit', planId);
    return (
        <Infobox title={'Raiteen geometriat'}>
            <InfoboxContent>
                <InfoboxField
                    label={'Vain kartalle osuvat geometriat'}
                    value={
                        <Checkbox
                            checked={useBoungingBox}
                            onChange={(e) => setUseBoundingBox(e.target.checked)}
                        />
                    }
                />
                {elements?.map((linking) => (
                    <InfoboxField
                        key={formatTrackMeter(linking.startAddress)}
                        label={
                            <span className={styles['location-track-geometry-infobox__plan-name']}>
                                {linking.planId ? (
                                    <Link
                                        onClick={() => onSelectPlan(linking.planId)}
                                        title={linking.planName}>
                                        {linking.planName}
                                    </Link>
                                ) : (
                                    'Ei suunnitelmaa'
                                )}
                            </span>
                        }
                        value={
                            <div>
                                <span>{formatTrackMeterWithoutMeters(linking.startAddress)}</span>{' '}
                                <span>{formatTrackMeterWithoutMeters(linking.endAddress)}</span>
                            </div>
                        }
                    />
                ))}
            </InfoboxContent>
        </Infobox>
    );
};
