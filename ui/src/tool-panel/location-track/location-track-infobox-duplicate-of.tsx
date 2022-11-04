import * as React from 'react';
import { LayoutLocationTrack, LayoutLocationTrackDuplicate } from 'track-layout/track-layout-model';
import { LocationTrackLink } from 'tool-panel/location-track/location-track-link';
import styles from './location-track-infobox.scss';

export type LocationTrackInfoboxDuplicateOfProps = {
    existingDuplicate: LayoutLocationTrack | undefined;
    duplicatesOfLocationTrack: LayoutLocationTrackDuplicate[] | undefined;
}

export const LocationTrackInfoboxDuplicateOf: React.FC<LocationTrackInfoboxDuplicateOfProps> = ({
    existingDuplicate,
    duplicatesOfLocationTrack,
}: LocationTrackInfoboxDuplicateOfProps) => {
    return (
        <React.Fragment>
            {
                existingDuplicate ?
                    <LocationTrackLink
                        locationTrackId={existingDuplicate.id}
                        locationTrackName={existingDuplicate.name}
                    />
                    : duplicatesOfLocationTrack ?
                       <ul className={styles['location-track-infobox-duplicate-of__ul']}> {
                            duplicatesOfLocationTrack.map(duplicate =>
                               <li key={duplicate.id}>
                                   <LocationTrackLink
                                       locationTrackId={duplicate.id}
                                       locationTrackName={duplicate.name}
                                   />
                               </li>
                            )
                        }
                       </ul>
                        : ''
            }
        </React.Fragment>
    );
};