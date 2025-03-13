import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './plan-download-popup.scss';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { LayoutLocationTrack, LayoutTrackNumber } from 'track-layout/track-layout-model';
import { kmNumberIsValid, LayoutContext } from 'common/common-model';
import { DownloadablePlan, PlanSelectionType } from 'map/plan-download/plan-download-store';
import { createDelegates } from 'store/store-utils';
import { Menu, menuDivider, menuOption } from 'vayla-design-lib/menu/menu';
import { GeometryPlanId, PlanApplicability } from 'geometry/geometry-model';
import { PlanDownloadAreaSection } from 'map/plan-download/plan-download-area-section';
import { PlanDownloadPlanSection } from 'map/plan-download/plan-download-plan-section';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import {
    getPlansLinkedToTrackNumber,
    getTrackNumberById,
} from 'track-layout/layout-track-number-api';
import {
    getReferenceLineStartAndEnd,
    getTrackNumberReferenceLine,
} from 'track-layout/layout-reference-line-api';
import { getChangeTimes } from 'common/change-time-api';
import {
    getLocationTrack,
    getLocationTrackStartAndEnd,
    getPlansLinkedToLocationTrack,
} from 'track-layout/layout-location-track-api';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { expectDefined } from 'utils/type-utils';
import {
    comparePlans,
    filterPlans,
    toDownloadablePlan,
} from 'map/plan-download/plan-download-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';

type PlanDownloadPopupSectionProps = {
    selectedType: PlanSelectionType | undefined;
    planSelectionType: PlanSelectionType;
    setPlanSelectionType: (planSelectionType: PlanSelectionType | undefined) => void;
    title: React.ReactNode;
    children?: React.ReactNode;
    disabled: boolean;
};
const PlanDownloadPopupSection: React.FC<PlanDownloadPopupSectionProps> = ({
    selectedType,
    planSelectionType,
    setPlanSelectionType,
    title,
    children,
    disabled,
}) => {
    const chevronClasses = createClassName(
        styles['plan-download-popup-chevron'],
        planSelectionType === selectedType && styles['plan-download-popup-chevron--visible'],
    );

    const titleContentClasses = createClassName(
        styles['plan-download-popup__title-content'],
        disabled && styles['plan-download-popup__title-content--disabled'],
    );

    return (
        <React.Fragment>
            <h2 className={styles['plan-download-popup__title']}>
                <Button
                    size={ButtonSize.X_SMALL}
                    className={chevronClasses}
                    variant={ButtonVariant.GHOST}
                    icon={Icons.Chevron}
                    disabled={disabled}
                    onClick={() =>
                        !disabled &&
                        setPlanSelectionType(
                            planSelectionType === selectedType ? undefined : planSelectionType,
                        )
                    }
                />
                <span className={titleContentClasses}>{title}</span>
            </h2>
            {planSelectionType === selectedType && (
                <div className={styles['plan-download-popup__content']}>{children}</div>
            )}
        </React.Fragment>
    );
};

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

export const PlanDownloadPopup: React.FC<PlanDownloadPopupProps> = ({ onClose, layoutContext }) => {
    const state = useTrackLayoutAppSelector((state) => state);
    const delegates = createDelegates(TrackLayoutActions);
    const planDownloadState = expectDefined(state.planDownloadState);

    const [trackNumberAndStartAndEnd, trackNumberFetchStatus] = useLoaderWithStatus(async () => {
        const trackNumber = planDownloadState.areaSelection.trackNumber
            ? await getTrackNumberById(planDownloadState.areaSelection.trackNumber, layoutContext)
            : undefined;
        const referenceLine = trackNumber
            ? await getTrackNumberReferenceLine(trackNumber.id, layoutContext)
            : undefined;
        const startAndEnd = referenceLine
            ? await getReferenceLineStartAndEnd(referenceLine.id, layoutContext)
            : undefined;
        return { trackNumber, startAndEnd };
    }, [planDownloadState.areaSelection.trackNumber]);
    const [locationTrackAndStartAndEnd, locationTrackFetchStatus] =
        useLoaderWithStatus(async () => {
            const locationTrack = planDownloadState.areaSelection.locationTrack
                ? await getLocationTrack(
                      planDownloadState.areaSelection?.locationTrack,
                      layoutContext,
                  )
                : undefined;
            const startAndEnd = locationTrack
                ? await getLocationTrackStartAndEnd(
                      locationTrack.id,
                      layoutContext,
                      getChangeTimes().layoutLocationTrack,
                  )
                : undefined;
            return { locationTrack, startAndEnd };
        }, [planDownloadState.areaSelection.locationTrack]);

    React.useEffect(() => {
        if (planDownloadState.areaSelection.locationTrack)
            delegates.setPlanDownloadAlignmentStartAndEnd(locationTrackAndStartAndEnd?.startAndEnd);
        else if (planDownloadState.areaSelection.trackNumber)
            delegates.setPlanDownloadAlignmentStartAndEnd(trackNumberAndStartAndEnd?.startAndEnd);
    }, [locationTrackAndStartAndEnd, trackNumberAndStartAndEnd]);

    const [linkedPlans, planFetchStatus] = useLoaderWithStatus<DownloadablePlan[]>(async () => {
        if (planDownloadState.areaSelection.locationTrack)
            return await getPlansLinkedToLocationTrack(
                layoutContext,
                planDownloadState.areaSelection.locationTrack,
                planDownloadState.areaSelection.startTrackMeter || undefined,
                planDownloadState.areaSelection.endTrackMeter || undefined,
            ).then((plans) =>
                plans.map((p) =>
                    toDownloadablePlan(
                        p,
                        planDownloadState.plans.find((sp) => sp.id === p.id)?.selected ?? false,
                    ),
                ),
            );
        else if (planDownloadState.areaSelection.trackNumber)
            return await getPlansLinkedToTrackNumber(
                layoutContext,
                planDownloadState.areaSelection.trackNumber,
                planDownloadState.areaSelection.startTrackMeter || undefined,
                planDownloadState.areaSelection.endTrackMeter || undefined,
            ).then((plans) =>
                plans.map((p) =>
                    toDownloadablePlan(
                        p,
                        planDownloadState.plans.find((sp) => sp.id === p.id)?.selected ?? false,
                    ),
                ),
            );
        else return [];
    }, [
        planDownloadState.areaSelection.locationTrack,
        planDownloadState.areaSelection.trackNumber,
        planDownloadState.areaSelection.startTrackMeter,
        planDownloadState.areaSelection.endTrackMeter,
        getChangeTimes().geometryPlan,
    ]);
    React.useEffect(() => {
        if (linkedPlans) delegates.setPlans(linkedPlans);
    }, [linkedPlans]);

    const menuAnchorRef = React.useRef<HTMLDivElement>(null);
    const [showFilterMenu, setShowFilterMenu] = React.useState(false);

    const { t } = useTranslation();

    const filterMenuItems = [
        menuOption(
            () =>
                delegates.setPlanDownloadApplicabilities(
                    toggleApplicability('PLANNING', planDownloadState.selectedApplicabilities),
                ),
            t('plan-download.applicable-for-plans'),
            'applicability-planning-filter',
            false,
            'CLOSE_MANUALLY',
            planDownloadState.selectedApplicabilities.includes('PLANNING') ? Icons.Tick : undefined,
        ),
        menuDivider(),
        menuOption(
            () =>
                delegates.setPlanDownloadApplicabilities(
                    toggleApplicability('MAINTENANCE', planDownloadState.selectedApplicabilities),
                ),
            t('plan-download.applicable-for-maintenance'),
            'applicability-planning-filter',
            false,
            'CLOSE_MANUALLY',
            planDownloadState.selectedApplicabilities.includes('MAINTENANCE')
                ? Icons.Tick
                : undefined,
        ),
        menuOption(
            () =>
                delegates.setPlanDownloadApplicabilities(
                    toggleApplicability('STATISTICS', planDownloadState.selectedApplicabilities),
                ),
            t('plan-download.applicable-for-statistics'),
            'applicability-planning-filter',
            false,
            'CLOSE_MANUALLY',
            planDownloadState.selectedApplicabilities.includes('STATISTICS')
                ? Icons.Tick
                : undefined,
        ),
    ];

    const titleClasses = createClassName(
        styles['plan-download-popup__title'],
        styles['plan-download-popup__title-content'],
    );

    const setPlanSelected = (id: GeometryPlanId, selected: boolean) => {
        delegates.setPlanDownloadPlanSelected({ id, selected });
    };
    const selectPlan = (id: GeometryPlanId) => delegates.onSelect({ geometryPlans: [id] });

    const plans = filterPlans(
        planDownloadState.plans,
        planDownloadState.selectedApplicabilities,
    ).toSorted(comparePlans);

    const disabled =
        layoutContext.publicationState !== 'OFFICIAL' || layoutContext.branch !== 'MAIN';

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
                planSelectionType={'AREA'}
                setPlanSelectionType={delegates.setPlanDownloadSelectionType}
                selectedType={planDownloadState.selectionType}
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
                planSelectionType={'PLAN'}
                setPlanSelectionType={delegates.setPlanDownloadSelectionType}
                selectedType={planDownloadState.selectionType}
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
                    </React.Fragment>
                }>
                <PlanDownloadPlanSection
                    plans={plans}
                    setPlanSelected={setPlanSelected}
                    setAllPlansSelected={delegates.setAllPlansSelected}
                    selectPlan={selectPlan}
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
