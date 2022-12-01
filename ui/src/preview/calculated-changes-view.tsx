import * as React from 'react';
import styles from './calculated-changes-view.scss';
import { CalculatedChanges } from 'publication/publication-api';
import { useTranslation } from 'react-i18next';
import { Accordion } from 'geoviite-design-lib/accordion/accordion';
import {
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutTrackNumber,
    LayoutTrackNumberId,
} from 'track-layout/track-layout-model';
import { filterNotEmpty } from 'utils/array-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { getTrackNumberById } from 'track-layout/layout-track-number-api';
import { getLocationTracks } from 'track-layout/layout-location-track-api';
import { getSwitches } from 'track-layout/layout-switch-api';

const calculatedChangesIsEmpty = (calculatedChanges: TrackNumberCalculatedChanges) => {
    return calculatedChanges.switches.length == 0 && calculatedChanges.locationTracks.length == 0;
};

type CalculatedChangesProps = {
    calculatedChanges: CalculatedChanges;
};

type TrackNumberCalculatedChanges = {
    trackNumber: LayoutTrackNumber;
    switches: LayoutSwitch[];
    locationTracks: LayoutLocationTrack[];
};

export const CalculatedChangesView: React.FC<CalculatedChangesProps> = ({
    calculatedChanges,
}: CalculatedChangesProps) => {
    const { t } = useTranslation();
    const [isFetching, setIsFetching] = React.useState(true);
    const [trackNumberCalculatedChanges, setTrackNumberCalculatedChanges] = React.useState<
        TrackNumberCalculatedChanges[]
    >([]);

    const [openTrackNumbers, setOpenTrackNumbers] = React.useState<{
        [key: LayoutTrackNumberId]: boolean;
    }>({});

    const toggle = (trackNumberId: LayoutTrackNumberId) => {
        setOpenTrackNumbers({
            ...openTrackNumbers,
            [trackNumberId]: !openTrackNumbers[trackNumberId],
        });
    };

    React.useEffect(() => {
        setIsFetching(true);

        const trackNumberPromises = Promise.all(
            calculatedChanges.trackNumberChanges.map((trackNumberChange) => {
                return getTrackNumberById(trackNumberChange.trackNumberId, 'DRAFT');
            }),
        );

        const locationTrackPromises = getLocationTracks(
            calculatedChanges.locationTracksChanges.map((t) => t.locationTrackId),
            'DRAFT',
        );

        const layoutSwitchPromises = getSwitches(
            calculatedChanges.switchChanges.map((t) => t.switchId),
            'DRAFT',
        );

        Promise.all([trackNumberPromises, locationTrackPromises, layoutSwitchPromises]).then(
            ([changedTrackNumbers, changedLocationTracks, changedLayoutSwitches]) => {
                const result = changedTrackNumbers.filter(filterNotEmpty).map((trackNumber) => {
                    const locationTracks = changedLocationTracks.filter(
                        (lt) => lt.trackNumberId == trackNumber.id,
                    );
                    const switches = changedLayoutSwitches.filter((layoutSwitch) =>
                        locationTracks.some((locationTrack) =>
                            calculatedChanges.switchChanges
                                .find((change) => change.switchId == layoutSwitch.id)
                                ?.changedJoints.some((j) => j.locationTrackId == locationTrack.id),
                        ),
                    );

                    return {
                        trackNumber,
                        locationTracks,
                        switches,
                    };
                });

                setTrackNumberCalculatedChanges(result);
                setIsFetching(false);
            },
        );
    }, [calculatedChanges]);

    return !isFetching ? (
        <section className={styles['calculated-changes-view']}>
            {trackNumberCalculatedChanges.length > 0 &&
                trackNumberCalculatedChanges.map(
                    (trackNumberChange: TrackNumberCalculatedChanges) => {
                        const hasCalculatedChanges = !calculatedChangesIsEmpty(trackNumberChange);
                        const trackNumber = trackNumberChange.trackNumber.number;
                        const trackNumberId = trackNumberChange.trackNumber.id;
                        const disabledMessage = hasCalculatedChanges
                            ? ''
                            : t('preview-view.disabled-message');

                        return (
                            <Accordion
                                key={trackNumberId}
                                header={t('preview-view.track-number', {
                                    number: trackNumber,
                                    disabled: disabledMessage,
                                })}
                                onToggle={() => toggle(trackNumberId)}
                                open={openTrackNumbers[trackNumberId]}
                                disabled={!hasCalculatedChanges}>
                                <React.Fragment>
                                    <p>
                                        {t('preview-view.calculated-changes-text', {
                                            number: trackNumber,
                                        })}
                                    </p>
                                    <ul>
                                        {trackNumberChange.locationTracks.length > 0 && (
                                            <li
                                                className={
                                                    styles[
                                                        'calculated-changes-view__location_tracks'
                                                    ]
                                                }>
                                                {t('preview-view.location-tracks')}
                                                <ul>
                                                    {trackNumberChange.locationTracks.map((lt) => (
                                                        <li key={lt.id}>{lt.name}</li>
                                                    ))}
                                                </ul>
                                            </li>
                                        )}

                                        {trackNumberChange.switches.length > 0 && (
                                            <li
                                                className={
                                                    styles['calculated-changes-view__switches']
                                                }>
                                                {t('preview-view.switches')}
                                                <ul>
                                                    {trackNumberChange.switches.map((s) => (
                                                        <li key={s.id}>{s.name}</li>
                                                    ))}
                                                </ul>
                                            </li>
                                        )}
                                    </ul>
                                </React.Fragment>
                            </Accordion>
                        );
                    },
                )}
            {trackNumberCalculatedChanges.length == 0 && (
                <>{t('preview-view.no-calculated-changes-text')}</>
            )}
        </section>
    ) : (
        <Spinner />
    );
};
