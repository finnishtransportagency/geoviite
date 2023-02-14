import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { BoundingBox } from 'model/geometry';
import { useLoader } from 'utils/react-utils';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import { formatTrackMeter } from 'utils/geography-utils';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { getGeometries } from 'track-layout/layout-location-track-api';

type LocationTrackGeometryInfoboxProps = {
    locationTrackId: LocationTrackId;
    boundingBox: BoundingBox | undefined;
};

export const LocationTrackGeometryInfobox: React.FC<LocationTrackGeometryInfoboxProps> = ({
    locationTrackId: _asd,
    boundingBox: _qwe,
}) => {
    const [useBoungingBox, setUseBoundingBox] = React.useState(true);
    const elements = useLoader(() => getGeometries(_asd).then((geom) => geom), [_asd]);
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
                        label={linking.planName ?? 'Ei suunnitelmaa'}
                        value={
                            <div>
                                <span>{formatTrackMeter(linking.startAddress)}</span>{' '}
                                <span>{formatTrackMeter(linking.endAddress)}</span>
                            </div>
                        }
                    />
                ))}
            </InfoboxContent>
        </Infobox>
    );
};
