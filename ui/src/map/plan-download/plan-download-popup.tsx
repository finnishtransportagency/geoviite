import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from './plan-download-popup.scss';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { KmNumber, LayoutContext, officialMainLayoutContext } from 'common/common-model';
import {
    DownloadablePlan,
    PlanDownloadAsset,
    PlanDownloadAssetType,
    PopupSection,
} from 'map/plan-download/plan-download-store';
import { createDelegates } from 'store/store-utils';
import { Menu, menuDivider, menuOption } from 'vayla-design-lib/menu/menu';
import { GeometryPlanId, PlanApplicability } from 'geometry/geometry-model';
import { PlanDownloadAreaSection } from 'map/plan-download/plan-download-area-section';
import { PlanDownloadPlanSection } from 'map/plan-download/plan-download-plan-section';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { exhaustiveMatchingGuard, expectDefined } from 'utils/type-utils';
import {
    comparePlans,
    fetchAssetAndExtremities,
    fetchDownloadablePlans,
    filterPlans,
} from 'map/plan-download/plan-download-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { PlanDownloadPopupSection } from 'map/plan-download/plan-download-popup-section';
import { createPortal } from 'react-dom';

type SpecifierValueProps = {
    assetLabel: string;
    assetName: string;
    selectedTrackRange: SelectedTrackRange;
};

const SpecifierValue: React.FC<SpecifierValueProps> = ({
    assetLabel,
    assetName,
    selectedTrackRange,
}) => {
    const displayKmNumber =
        selectedTrackRange.start !== undefined || selectedTrackRange.end !== undefined;

    return (
        <React.Fragment>
            <span>{assetLabel} </span>
            <span className={styles['plan-download-popup__title-asset-name']}>{assetName}</span>
            {displayKmNumber && (
                <span>
                    , {selectedTrackRange.start}-{selectedTrackRange.end}
                </span>
            )}
        </React.Fragment>
    );
};

type SelectedTrackRange = {
    start: KmNumber | undefined;
    end: KmNumber | undefined;
};

type LocationSpecifierProps = {
    selectedAsset: PlanDownloadAsset | undefined;
    selectedTrackRange: SelectedTrackRange;
};
export const LocationSpecifier: React.FC<LocationSpecifierProps> = ({
    selectedAsset,
    selectedTrackRange,
}) => {
    const { t } = useTranslation();

    switch (selectedAsset?.type) {
        case PlanDownloadAssetType.TRACK_NUMBER: {
            return (
                <SpecifierValue
                    assetLabel={t('plan-download.track-number')}
                    assetName={selectedAsset.asset.number}
                    selectedTrackRange={selectedTrackRange}
                />
            );
        }

        case PlanDownloadAssetType.LOCATION_TRACK:
            return (
                <SpecifierValue
                    assetLabel={t('plan-download.location-track')}
                    assetName={selectedAsset.asset.name}
                    selectedTrackRange={selectedTrackRange}
                />
            );

        case undefined:
            return <React.Fragment />;

        default:
            return exhaustiveMatchingGuard(selectedAsset);
    }
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
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = createDelegates(TrackLayoutActions);
    const planDownloadState = expectDefined(state.planDownloadState);

    const [assetAndStartAndEnd, assetFetchStatus] = useLoaderWithStatus(
        () =>
            planDownloadState.areaSelection.asset
                ? fetchAssetAndExtremities(
                      planDownloadState.areaSelection.asset,
                      officialMainLayoutContext(),
                  )
                : Promise.resolve(undefined),
        [
            planDownloadState.areaSelection.asset,
            changeTimes.layoutLocationTrack,
            changeTimes.layoutTrackNumber,
        ],
    );

    React.useEffect(() => {
        delegates.setPlanDownloadAlignmentStartAndEnd(assetAndStartAndEnd?.startAndEnd);
    }, [assetAndStartAndEnd]);

    const [linkedPlans, planFetchStatus] = useLoaderWithStatus<DownloadablePlan[]>(
        // NOTE: This operates using main-official by design. The popup can be visible in non-main-offical contexts
        // (even if it is mostly disabled), and we don't want it to fetch plans in those contexts
        () => fetchDownloadablePlans(planDownloadState.areaSelection, officialMainLayoutContext()),
        [
            planDownloadState.areaSelection.asset,
            planDownloadState.areaSelection.startTrackMeter,
            planDownloadState.areaSelection.endTrackMeter,
            changeTimes.geometryPlan,
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
    const selectPlanInToolPanel = (id: GeometryPlanId) => {
        delegates.onSelect({ geometryPlans: [id] });
        delegates.setToolPanelTab({ id, type: 'GEOMETRY_PLAN' });
    };

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

    return createPortal(
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
                title={
                    <React.Fragment>
                        <span>{t('plan-download.area')}</span>
                        <span>
                            <LocationSpecifier
                                selectedAsset={assetAndStartAndEnd}
                                selectedTrackRange={{
                                    start: planDownloadState.areaSelection.startTrackMeter,
                                    end: planDownloadState.areaSelection.endTrackMeter,
                                }}
                            />
                        </span>
                    </React.Fragment>
                }>
                {planDownloadState.areaSelection && (
                    <PlanDownloadAreaSection
                        layoutContext={officialMainLayoutContext()}
                        selectedAsset={assetAndStartAndEnd}
                        state={planDownloadState}
                        onCommitField={delegates.onCommitPlanDownloadAreaSelectionField}
                        onUpdateProp={delegates.onUpdatePlanDownloadAreaSelectionProp}
                        loading={assetFetchStatus === LoaderStatus.Loading}
                        disabled={disabled}
                    />
                )}
            </PlanDownloadPopupSection>
            <PlanDownloadPopupSection
                selected={planDownloadState.openPopupSection === 'PLAN'}
                toggleOpen={() => toggleSectionOpen('PLAN')}
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
                    asset={planDownloadState.areaSelection.asset}
                    startKm={planDownloadState.areaSelection.startTrackMeter}
                    endKm={planDownloadState.areaSelection.endTrackMeter}
                    selectedApplicabilities={planDownloadState.selectedApplicabilities}
                />
            </PlanDownloadPopupSection>
        </div>,
        document.body,
    );
};
