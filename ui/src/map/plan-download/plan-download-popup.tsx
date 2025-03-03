import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './plan-download-popup.scss';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import {
    LayoutLocationTrack,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { LayoutContext, trackMeterIsValid } from 'common/common-model';
import { useLocationTrack, useTrackNumber } from 'track-layout/track-layout-react-utils';
import {
    initialPlanDownloadStateFromSelection,
    planDownloadActions,
    planDownloadReducer,
    PlanDownloadState,
    PlanSelectionType,
} from 'map/plan-download/plan-download-slice';
import { UnknownAction } from 'redux';
import { createDelegatesWithDispatcher } from 'store/store-utils';
import { Menu, menuDivider, menuOption } from 'vayla-design-lib/menu/menu';
import { PlanApplicability } from 'geometry/geometry-model';
import { PlanDownloadAreaSection } from 'map/plan-download/plan-download-area-section';

type PlanDownloadPopupSectionProps = {
    selectedType: PlanSelectionType | undefined;
    planSelectionType: PlanSelectionType;
    setPlanSelectionType: (planSelectionType: PlanSelectionType | undefined) => void;
    title: React.ReactNode;
    children?: React.ReactNode;
};
const PlanDownloadPopupSection: React.FC<PlanDownloadPopupSectionProps> = ({
    selectedType,
    planSelectionType,
    setPlanSelectionType,
    title,
    children,
}) => {
    const chevronClasses = createClassName(
        styles['plan-download-popup-chevron'],
        planSelectionType === selectedType && styles['plan-download-popup-chevron--visible'],
    );

    return (
        <React.Fragment>
            <h2 className={styles['plan-download-popup__title']}>
                <span
                    className={chevronClasses}
                    onClick={() =>
                        setPlanSelectionType(
                            planSelectionType === selectedType ? undefined : planSelectionType,
                        )
                    }>
                    <Icons.Chevron size={IconSize.SMALL} />
                </span>
                <span className={styles['plan-download-popup__title-content']}>{title}</span>
            </h2>
            {planSelectionType === selectedType && (
                <div className={styles['plan-download-popup__content']}>{children}</div>
            )}
        </React.Fragment>
    );
};

const trackMeterRange = (start: string, end: string) => {
    const startOrUndefined = trackMeterIsValid(start) ? start : undefined;
    const endOrUndefined = trackMeterIsValid(end) ? end : undefined;

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
    selectedLocationTrackId: LocationTrackId | undefined;
    selectedTrackNumberId: LayoutTrackNumberId | undefined;
};

const toggleApplicability = (
    applicability: PlanApplicability,
    applicabilities: PlanApplicability[],
) =>
    applicabilities.includes(applicability)
        ? applicabilities.filter((a) => a !== applicability)
        : [...applicabilities, applicability];

export const PlanDownloadPopup: React.FC<PlanDownloadPopupProps> = ({
    onClose,
    layoutContext,
    selectedTrackNumberId,
    selectedLocationTrackId,
}) => {
    const [state, dispatcher] = React.useReducer<PlanDownloadState, [action: UnknownAction]>(
        planDownloadReducer,
        initialPlanDownloadStateFromSelection(selectedLocationTrackId, selectedTrackNumberId),
    );

    const stateActions = createDelegatesWithDispatcher(dispatcher, planDownloadActions);
    const trackNumber = useTrackNumber(state.areaSelection?.trackNumber, layoutContext);
    const locationTrack = useLocationTrack(state.areaSelection?.locationTrack, layoutContext);
    const menuAnchorRef = React.useRef<HTMLDivElement>(null);
    const [showFilterMenu, setShowFilterMenu] = React.useState(false);

    const { t } = useTranslation();

    const filterMenuItems = [
        menuOption(
            () =>
                stateActions.setApplicabilities(
                    toggleApplicability('PLANNING', state.selectedApplicabilities),
                ),
            t('plan-download.applicable-for-plans'),
            'applicability-planning-filter',
            false,
            'CLOSE_MANUALLY',
            state.selectedApplicabilities.includes('PLANNING') ? Icons.Tick : undefined,
        ),
        menuDivider(),
        menuOption(
            () =>
                stateActions.setApplicabilities(
                    toggleApplicability('MAINTENANCE', state.selectedApplicabilities),
                ),
            t('plan-download.applicable-for-maintenance'),
            'applicability-planning-filter',
            false,
            'CLOSE_MANUALLY',
            state.selectedApplicabilities.includes('MAINTENANCE') ? Icons.Tick : undefined,
        ),
        menuOption(
            () =>
                stateActions.setApplicabilities(
                    toggleApplicability('STATISTICS', state.selectedApplicabilities),
                ),
            t('plan-download.applicable-for-statistics'),
            'applicability-planning-filter',
            false,
            'CLOSE_MANUALLY',
            state.selectedApplicabilities.includes('STATISTICS') ? Icons.Tick : undefined,
        ),
    ];

    const titleClasses = createClassName(
        styles['plan-download-popup__title'],
        styles['plan-download-popup__title-content'],
    );

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
                setPlanSelectionType={stateActions.setSelectionType}
                selectedType={state.selectionType}
                title={
                    <React.Fragment>
                        <span>{t('plan-download.area')}</span>
                        <span>
                            <LocationSpecifier
                                trackNumber={trackNumber}
                                locationTrack={locationTrack}
                                startTrackMeter={state.areaSelection?.startTrackMeter}
                                endTrackMeter={state.areaSelection?.endTrackMeter}
                            />
                        </span>
                    </React.Fragment>
                }>
                {state.areaSelection && (
                    <PlanDownloadAreaSection
                        layoutContext={layoutContext}
                        trackNumber={trackNumber}
                        locationTrack={locationTrack}
                        state={state}
                        onCommitField={stateActions.onCommitField}
                        onUpdateProp={stateActions.onUpdateProp}
                    />
                )}
            </PlanDownloadPopupSection>
            <PlanDownloadPopupSection
                planSelectionType={'PLAN'}
                setPlanSelectionType={stateActions.setSelectionType}
                selectedType={state.selectionType}
                title={
                    <React.Fragment>
                        <span>{t('plan-download.plans', { amount: 'N' })}</span>
                        <div ref={menuAnchorRef}>
                            <Button
                                size={ButtonSize.X_SMALL}
                                variant={ButtonVariant.GHOST}
                                icon={Icons.Filter}
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
                HOJOOOOOOOOOOOOOOOOO
            </PlanDownloadPopupSection>
        </div>
    );
};
