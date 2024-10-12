import * as React from 'react';
import styles from './calculated-changes-view.scss';
import { useTranslation } from 'react-i18next';
import { Accordion } from 'geoviite-design-lib/accordion/accordion';
import {
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutTrackNumber,
    LayoutTrackNumberId,
} from 'track-layout/track-layout-model';
import { filterNotEmpty, filterUnique, groupBy } from 'utils/array-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { getLocationTracks } from 'track-layout/layout-location-track-api';
import { getSwitches } from 'track-layout/layout-switch-api';
import { CalculatedChanges } from 'publication/publication-model';
import { draftLayoutContext, LayoutContext } from 'common/common-model';

const calculatedChangesIsEmpty = (calculatedChanges: GroupedCalculatedChanges) => {
    return calculatedChanges.switches.length == 0 && calculatedChanges.locationTracks.length == 0;
};

type CalculatedChangesProps = {
    calculatedChanges: CalculatedChanges;
    layoutContext: LayoutContext;
};

type GroupedCalculatedChanges = {
    trackNumber: LayoutTrackNumber;
    switches: LayoutSwitch[];
    locationTracks: LayoutLocationTrack[];
};

export const CalculatedChangesView: React.FC<CalculatedChangesProps> = ({
    calculatedChanges,
    layoutContext,
}: CalculatedChangesProps) => {
    const { t } = useTranslation();
    const [isFetching, setIsFetching] = React.useState(true);
    const [groupedCalculatedChanges, setGroupedCalculatedChanges] = React.useState<
        GroupedCalculatedChanges[]
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
        const layoutContextDraft = draftLayoutContext(layoutContext);

        const trackNumberChanges = calculatedChanges.indirectChanges.trackNumberChanges;
        const locationTrackChanges = calculatedChanges.indirectChanges.locationTrackChanges;
        const switchChanges = calculatedChanges.indirectChanges.switchChanges;

        const trackNumbersPromise = getTrackNumbers(layoutContextDraft);
        const locationTracksPromise = getLocationTracks(
            locationTrackChanges.map((lt) => lt.locationTrackId),
            layoutContextDraft,
        );
        const switchesPromise = getSwitches(
            switchChanges.map((s) => s.switchId),
            layoutContextDraft,
        );

        Promise.all([trackNumbersPromise, locationTracksPromise, switchesPromise]).then(
            ([trackNumbers, locationTracks, switches]) => {
                type Change = {
                    trackNumberId: LayoutTrackNumberId;
                    locationTrack?: LayoutLocationTrack;
                    switch?: LayoutSwitch;
                };

                const tnChanges: Change[] = trackNumberChanges.map((tnc) => ({
                    trackNumberId: tnc.trackNumberId,
                }));

                const ltChanges: Change[] = locationTrackChanges
                    .map((ltc) => locationTracks.find((lt) => lt.id == ltc.locationTrackId))
                    .filter(filterNotEmpty)
                    .map((lt) => ({
                        trackNumberId: lt.trackNumberId,
                        locationTrack: lt,
                    }));

                const sChanges: Change[] = switchChanges.flatMap((sc) => {
                    return sc.changedJoints
                        .map((cj) => cj.trackNumberId)
                        .filter(filterUnique)
                        .map((tn) => ({
                            trackNumberId: tn,
                            switch: switches.find((s) => s.id == sc.switchId),
                        }));
                });

                const groupedChanges = groupBy(
                    [...tnChanges, ...ltChanges, ...sChanges],
                    (o) => o.trackNumberId,
                );

                const calculatedChanges = Object.entries(groupedChanges)
                    .map(([key, changes]) => {
                        const trackNumber = trackNumbers.find((tn) => tn.id == key);
                        return trackNumber
                            ? {
                                  trackNumber: trackNumber,
                                  locationTracks: changes
                                      .map((c) => c.locationTrack)
                                      .filter(filterNotEmpty),
                                  switches: changes.map((c) => c.switch).filter(filterNotEmpty),
                              }
                            : undefined;
                    })
                    .filter(filterNotEmpty);

                setGroupedCalculatedChanges(calculatedChanges);
                setIsFetching(false);
            },
        );
    }, [calculatedChanges]);

    return !isFetching ? (
        <section className={styles['calculated-changes-view']}>
            <h3>{t('preview-view.track-address-changes')}</h3>
            <div className={styles['calculated-changes-view__changes-list']}>
                {groupedCalculatedChanges.map((changes: GroupedCalculatedChanges) => {
                    const hasCalculatedChanges = !calculatedChangesIsEmpty(changes);
                    const trackNumber = changes.trackNumber.number;
                    const trackNumberId = changes.trackNumber.id;
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
                            open={openTrackNumbers[trackNumberId] ?? false}
                            disabled={!hasCalculatedChanges}>
                            <React.Fragment>
                                <ul
                                    className={
                                        styles['calculated-changes-view__track-number-changes']
                                    }>
                                    {changes.locationTracks.length > 0 && (
                                        <li
                                            className={
                                                styles['calculated-changes-view__location_tracks']
                                            }>
                                            {t('preview-view.location-tracks')}
                                            <ul>
                                                {changes.locationTracks.map((lt) => (
                                                    <li key={lt.id}>{lt.name}</li>
                                                ))}
                                            </ul>
                                        </li>
                                    )}

                                    {changes.switches.length > 0 && (
                                        <li className={styles['calculated-changes-view__switches']}>
                                            {t('preview-view.switches')}
                                            <ul>
                                                {changes.switches.map((s) => (
                                                    <li key={s.id}>{s.name}</li>
                                                ))}
                                            </ul>
                                        </li>
                                    )}
                                </ul>
                            </React.Fragment>
                        </Accordion>
                    );
                })}
                {groupedCalculatedChanges.length == 0 && (
                    <React.Fragment>{t('preview-view.no-calculated-changes-text')}</React.Fragment>
                )}
            </div>
        </section>
    ) : (
        <Spinner />
    );
};
