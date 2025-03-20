import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './plan-download-popup.scss';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { LayoutLocationTrack, LayoutTrackNumber } from 'track-layout/track-layout-model';
import { kmNumberIsValid, LayoutContext } from 'common/common-model';
import { DownloadablePlan, PopupSection } from 'map/plan-download/plan-download-store';
import { createDelegates } from 'store/store-utils';
import { Menu, menuDivider, menuOption } from 'vayla-design-lib/menu/menu';
import { GeometryPlanId, PlanApplicability } from 'geometry/geometry-model';
import { PlanDownloadAreaSection } from 'map/plan-download/plan-download-area-section';
import { PlanDownloadPlanSection } from 'map/plan-download/plan-download-plan-section';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { getChangeTimes } from 'common/change-time-api';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { expectDefined } from 'utils/type-utils';
import {
    comparePlans,
    fetchLocationTrackAndExtremities,
    fetchDownloadablePlans,
    fetchTrackNumberAndExtremities,
    filterPlans,
} from 'map/plan-download/plan-download-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { PlanDownloadPopupSection } from 'map/plan-download/plan-download-popup-section';

const trackMeterRange = (start: string, end: string) => {
    const startOrUndefined = kmNumberIsValid(start) ? start : undefined;
    const endOrUndefined = kmNumberIsValid(end) ? end : undefined;

    if (startOrUndefined && endOrUndefined) return `${start}-${end}`;
    if (startOrUndefined) return `${start}-`;
    if (endOrUndefined) return `-${end}`;
    return '';
};

type LocationSpecifierProps = {
    trackNumber: LayoutTrackNumber | undefined;
    locationTrack: LayoutLocationTrack | undefined;
    startTrackMeter: string;
    endTrackMeter: string;
};
export const LocationSpecifier: React.FC<LocationSpecifierProps> = ({
    trackNumber,
    locationTrack,
    startTrackMeter,
    endTrackMeter,
}) => {
    const { t } = useTranslation();
    const base = locationTrack
        ? `${t('plan-download.location-track')} ${locationTrack.name}`
        : trackNumber
          ? `${t('plan-download.track-number')} ${trackNumber.number}`
          : '';
    const trackMeter = trackMeterRange(startTrackMeter, endTrackMeter);
    return (
        <React.Fragment>
            {!locationTrack && !trackNumber ? '' : !trackMeter ? base : `${base}, ${trackMeter}`}
        </React.Fragment>
    );
};

type PlanDownloadPopupProps = {
    layoutContext: LayoutContext;
    onClose: () => void;
};

const toggleApplicability = (
    applicability: PlanApplicability,
    applicabilities: PlanApplicability[],
) =>
    applicabilities.includes(applicability)
        ? applicabilities.filter((a) => a !== applicability)
        : [...applicabilities, applicability];

const filterMenuOption = (onSelect: () => void, name: string, qaId: string, isChecked: boolean) =>
    menuOption(onSelect, name, qaId, false, 'CLOSE_MANUALLY', isChecked ? Icons.Tick : undefined);

export const PlanDownloadPopup: React.FC<PlanDownloadPopupProps> = ({ onClose, layoutContext }) => {
    const { t } = useTranslation();

    const state = useTrackLayoutAppSelector((state) => state);
    const delegates = createDelegates(TrackLayoutActions);
    const planDownloadState = expectDefined(state.planDownloadState);

    const [trackNumberAndStartAndEnd, trackNumberFetchStatus] = useLoaderWithStatus(
        () =>
            fetchTrackNumberAndExtremities(
                planDownloadState.areaSelection.trackNumber,
                layoutContext,
            ),
        [planDownloadState.areaSelection.trackNumber],
    );
    const [locationTrackAndStartAndEnd, locationTrackFetchStatus] = useLoaderWithStatus(
        () =>
            fetchLocationTrackAndExtremities(
                planDownloadState.areaSelection.locationTrack,
                layoutContext,
            ),
        [planDownloadState.areaSelection.locationTrack],
    );

    React.useEffect(() => {
        if (planDownloadState.areaSelection.locationTrack)
            delegates.setPlanDownloadAlignmentStartAndEnd(locationTrackAndStartAndEnd?.startAndEnd);
        else if (planDownloadState.areaSelection.trackNumber)
            delegates.setPlanDownloadAlignmentStartAndEnd(trackNumberAndStartAndEnd?.startAndEnd);
    }, [locationTrackAndStartAndEnd, trackNumberAndStartAndEnd]);

    const [linkedPlans, planFetchStatus] = useLoaderWithStatus<DownloadablePlan[]>(
        () => fetchDownloadablePlans(planDownloadState.areaSelection, layoutContext),
        [
            planDownloadState.areaSelection.locationTrack,
            planDownloadState.areaSelection.trackNumber,
            planDownloadState.areaSelection.startTrackMeter,
            planDownloadState.areaSelection.endTrackMeter,
            getChangeTimes().geometryPlan,
        ],
    );

    const menuAnchorRef = React.useRef<HTMLDivElement>(null);
    const [showFilterMenu, setShowFilterMenu] = React.useState(false);

    const titleClasses = createClassName(
        styles['plan-download-popup__title-container'],
        styles['plan-download-popup__title'],
        styles['plan-download-popup__title-content'],
    );

    const togglePlanForDownload = (id: GeometryPlanId, selected: boolean) => {
        delegates.togglePlanForDownload({ id, selected });
    };
    const selectPlanInToolPanel = (id: GeometryPlanId) =>
        delegates.onSelect({ geometryPlans: [id] });

    const plans = filterPlans(
        linkedPlans ?? [],
        planDownloadState.selectedApplicabilities,
        planDownloadState.includingPaikannuspalvelu,
    ).toSorted(comparePlans);

    const disabled =
        layoutContext.publicationState !== 'OFFICIAL' || layoutContext.branch !== 'MAIN';

    const filterMenuItems = [
        filterMenuOption(
            () =>
                delegates.setIncludingPaikannuspalvelu(
                    !planDownloadState.includingPaikannuspalvelu,
                ),
            t('plan-download.paikannuspalvelu-plans'),
            'include-paikannuspalvelu-filter',
            planDownloadState.includingPaikannuspalvelu,
        ),
        menuDivider(),
        filterMenuOption(
            () =>
                delegates.setPlanDownloadApplicabilities(
                    toggleApplicability('PLANNING', planDownloadState.selectedApplicabilities),
                ),
            t('plan-download.applicable-for-plans'),
            'applicability-planning-filter',
            planDownloadState.selectedApplicabilities.includes('PLANNING'),
        ),
        menuDivider(),
        filterMenuOption(
            () =>
                delegates.setPlanDownloadApplicabilities(
                    toggleApplicability('MAINTENANCE', planDownloadState.selectedApplicabilities),
                ),
            t('plan-download.applicable-for-maintenance'),
            'applicability-planning-filter',
            planDownloadState.selectedApplicabilities.includes('MAINTENANCE'),
        ),
        filterMenuOption(
            () =>
                delegates.setPlanDownloadApplicabilities(
                    toggleApplicability('STATISTICS', planDownloadState.selectedApplicabilities),
                ),
            t('plan-download.applicable-for-statistics'),
            'applicability-planning-filter',
            planDownloadState.selectedApplicabilities.includes('STATISTICS'),
        ),
    ];

    const toggleSectionOpen = (section: PopupSection) =>
        planDownloadState.openPopupSection === section
            ? delegates.setOpenPopupSection(undefined)
            : delegates.setOpenPopupSection(section);

    return (
        <div className={styles['plan-download-popup']}>
            <h1 className={titleClasses}>
                {t('plan-download.title')}
                <span className={styles['plan-download-popup__close']}>
                    <Button
                        variant={ButtonVariant.GHOST}
                        size={ButtonSize.X_SMALL}
                        icon={Icons.Close}
                        onClick={onClose}
                    />
                </span>
            </h1>
            <PlanDownloadPopupSection
                selected={planDownloadState.openPopupSection === 'AREA'}
                toggleOpen={() => toggleSectionOpen('AREA')}
                disabled={disabled}
                title={
                    <React.Fragment>
                        <span>{t('plan-download.area')}</span>
                        <span>
                            <LocationSpecifier
                                trackNumber={trackNumberAndStartAndEnd?.trackNumber}
                                locationTrack={locationTrackAndStartAndEnd?.locationTrack}
                                startTrackMeter={planDownloadState.areaSelection.startTrackMeter}
                                endTrackMeter={planDownloadState.areaSelection.endTrackMeter}
                            />
                        </span>
                    </React.Fragment>
                }>
                {planDownloadState.areaSelection && (
                    <PlanDownloadAreaSection
                        layoutContext={layoutContext}
                        trackNumber={trackNumberAndStartAndEnd?.trackNumber}
                        locationTrack={locationTrackAndStartAndEnd?.locationTrack}
                        state={planDownloadState}
                        onCommitField={delegates.onCommitPlanDownloadAreaSelectionField}
                        onUpdateProp={delegates.onUpdatePlanDownloadAreaSelectionProp}
                        loading={
                            planDownloadState.areaSelection.locationTrack
                                ? locationTrackFetchStatus !== LoaderStatus.Ready
                                : planDownloadState.areaSelection.trackNumber
                                  ? trackNumberFetchStatus !== LoaderStatus.Ready
                                  : false
                        }
                        disabled={disabled}
                    />
                )}
            </PlanDownloadPopupSection>
            <PlanDownloadPopupSection
                selected={planDownloadState.openPopupSection === 'PLAN'}
                toggleOpen={() => toggleSectionOpen('PLAN')}
                disabled={disabled}
                title={
                    <React.Fragment>
                        {planFetchStatus === LoaderStatus.Ready ? (
                            <span>
                                {t('plan-download.plans', {
                                    amount: plans.length,
                                })}
                            </span>
                        ) : (
                            <Spinner />
                        )}
                    </React.Fragment>
                }
                titleButtons={
                    <div ref={menuAnchorRef}>
                        <Button
                            size={ButtonSize.X_SMALL}
                            variant={ButtonVariant.GHOST}
                            icon={Icons.Filter}
                            disabled={disabled}
                            onClick={() => setShowFilterMenu(!showFilterMenu)}
                        />
                        {showFilterMenu && (
                            <Menu
                                anchorElementRef={menuAnchorRef}
                                onClickOutside={() => setShowFilterMenu(false)}
                                items={filterMenuItems}
                                onClose={() => setShowFilterMenu(false)}
                                opensTowards={'LEFT'}
                            />
                        )}
                    </div>
                }>
                <PlanDownloadPlanSection
                    plans={plans}
                    selectedPlanIds={planDownloadState.selectedPlans}
                    togglePlanForDownload={togglePlanForDownload}
                    selectPlansForDownload={delegates.selectMultiplePlansForDownload}
                    unselectAllPlans={delegates.unselectPlansForDownload}
                    selectPlanInToolPanel={selectPlanInToolPanel}
                    disabled={disabled}
                    trackNumberId={planDownloadState.areaSelection.trackNumber}
                    locationTrackId={planDownloadState.areaSelection.locationTrack}
                    startKm={planDownloadState.areaSelection.startTrackMeter}
                    endKm={planDownloadState.areaSelection.endTrackMeter}
                    selectedApplicabilities={planDownloadState.selectedApplicabilities}
                />
            </PlanDownloadPopupSection>
        </div>
    );
};
