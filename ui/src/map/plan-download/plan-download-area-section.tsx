import * as React from 'react';
import { useTranslation } from 'react-i18next';
import {
    LocationTrackItemValue,
    SearchDropdown,
    SearchItemType,
    SearchItemValue,
    SearchType,
    TrackNumberItemValue,
} from 'tool-bar/search-dropdown';
import { LayoutContext, officialMainLayoutContext } from 'common/common-model';
import {
    AreaSelection,
    PlanDownloadAsset,
    PlanDownloadAssetAndExtremities,
    PlanDownloadAssetType,
    PlanDownloadState,
} from 'map/plan-download/plan-download-store';
import {
    filterErrors,
    filterWarnings,
    getVisibleErrorsByProp,
    getVisibleIssuesAndParamsByProp,
    hasErrors,
    PropEdit,
} from 'utils/validation-utils';
import { createClassName } from 'vayla-design-lib/utils';
import styles from 'map/plan-download/plan-download-popup.scss';
import { Radio } from 'vayla-design-lib/radio/radio';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { error } from 'geoviite-design-lib/snackbar/snackbar';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { useLoader } from 'utils/react-utils';
import { getKmPostsOnTrackNumber } from 'track-layout/layout-km-post-api';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';

type AreaSelectionType = 'TRACK_METERS' | 'MAINTENANCE_AREA' | 'MAP_AREA';
const ASSET_SEARCH_TYPES = [SearchType.TRACK_NUMBER, SearchType.LOCATION_TRACK];

const inferDropdownItemValue = (
    downloadAsset: PlanDownloadAsset | undefined,
): TrackNumberItemValue | LocationTrackItemValue | undefined => {
    switch (downloadAsset?.type) {
        case PlanDownloadAssetType.TRACK_NUMBER:
            return {
                trackNumber: downloadAsset.asset,
                type: SearchItemType.TRACK_NUMBER,
            };
        case PlanDownloadAssetType.LOCATION_TRACK:
            return {
                locationTrack: downloadAsset.asset,
                type: SearchItemType.LOCATION_TRACK,
            };
        case undefined:
            return undefined;
        default:
            return exhaustiveMatchingGuard(downloadAsset);
    }
};

const getPopupAssetTrackNumberId = (downloadAsset: PlanDownloadAsset | undefined) => {
    switch (downloadAsset?.type) {
        case PlanDownloadAssetType.TRACK_NUMBER:
            return downloadAsset.asset.id;
        case PlanDownloadAssetType.LOCATION_TRACK:
            return downloadAsset.asset.trackNumberId;
        case undefined:
            return undefined;
        default:
            return exhaustiveMatchingGuard(downloadAsset);
    }
};

export const PlanDownloadAreaSection: React.FC<{
    layoutContext: LayoutContext;
    selectedAsset: PlanDownloadAssetAndExtremities | undefined;
    state: PlanDownloadState;
    onUpdateProp: <TKey extends keyof AreaSelection>(
        propEdit: PropEdit<AreaSelection, TKey>,
    ) => void;
    onCommitField: (field: keyof AreaSelection) => void;
    loading: boolean;
    disabled: boolean;
}> = ({ layoutContext, selectedAsset, state, onUpdateProp, onCommitField, loading, disabled }) => {
    const { t } = useTranslation();
    const [areaSelectionType, setAreaSelectionType] =
        React.useState<AreaSelectionType>('TRACK_METERS');
    const kmPostsOnTrackNumber =
        useLoader(async () => {
            const trackNumberId = getPopupAssetTrackNumberId(selectedAsset);

            const kmPosts = trackNumberId
                ? await getKmPostsOnTrackNumber(officialMainLayoutContext(), trackNumberId)
                : [];
            const startKm = selectedAsset?.startAndEnd?.start?.address?.kmNumber;
            const endKm = selectedAsset?.startAndEnd?.end?.address?.kmNumber;
            
            return startKm && endKm
                ? kmPosts.filter((kmPost) => kmPost.kmNumber >= startKm && kmPost.kmNumber <= endKm)
                : kmPosts;
        }, [selectedAsset?.asset.id, selectedAsset?.type])?.map((km) => ({
            name: km.kmNumber,
            value: km.kmNumber,
            qaId: `km-${km.kmNumber}`,
        })) || [];

    const updateProp = <TKey extends keyof AreaSelection>(
        key: TKey,
        value: AreaSelection[TKey],
    ) => {
        onUpdateProp({
            key: key,
            value: value,
            editingExistingValue: false,
        });
    };

    const onItemSelected = (item: SearchItemValue) => {
        switch (item.type) {
            case SearchItemType.TRACK_NUMBER:
                return updateProp('asset', {
                    id: item.trackNumber.id,
                    type: PlanDownloadAssetType.TRACK_NUMBER,
                });
            case SearchItemType.LOCATION_TRACK:
                return updateProp('asset', {
                    id: item.locationTrack.id,
                    type: PlanDownloadAssetType.LOCATION_TRACK,
                });
            case SearchItemType.SWITCH:
            case SearchItemType.OPERATING_POINT:
                return error(t('plan-download.unsupported-result-type', { type: item.type }));
            default:
                return exhaustiveMatchingGuard(item);
        }
    };
    const hasEndBeforeStartError = getVisibleErrorsByProp(
        state.committedFields,
        state.validationIssues,
        'endTrackMeter',
    ).includes('end-before-start');

    const labelClasses = (hasErrors: boolean) =>
        createClassName(
            hasErrors && styles['plan-download-popup__field-label--error'],
            disabled && styles['plan-download-popup__field-label--disabled'],
        );

    const selectedDropdownValue = inferDropdownItemValue(selectedAsset);

    const getName = (item: SearchItemValue) => {
        switch (item.type) {
            case SearchItemType.TRACK_NUMBER:
                return item.trackNumber.number;
            case SearchItemType.LOCATION_TRACK:
                return item.locationTrack.name;
            case SearchItemType.SWITCH:
            case SearchItemType.OPERATING_POINT:
                console.error('Unsupported item type', item.type);
                return '';
            default:
                return exhaustiveMatchingGuard(item);
        }
    };
    const errors = state.validationIssues.filter(filterErrors);
    const warnings = state.validationIssues.filter(filterWarnings);

    return (
        <React.Fragment>
            <div>
                <div className={styles['plan-download-popup__radio-container']}>
                    <Radio
                        checked={areaSelectionType === 'TRACK_METERS'}
                        disabled={disabled}
                        onChange={() => setAreaSelectionType('TRACK_METERS')}>
                        {t('plan-download.track-meter-range')}
                    </Radio>
                </div>
                {areaSelectionType === 'TRACK_METERS' && (
                    <React.Fragment>
                        <FieldLayout
                            value={
                                <div className={styles['plan-download-popup__area-grid']}>
                                    <label
                                        className={labelClasses(
                                            hasErrors(state.committedFields, errors, 'asset'),
                                        )}>
                                        {t('plan-download.track-number-or-location-track')}
                                    </label>
                                    <SearchDropdown
                                        disabled={disabled}
                                        searchTypes={ASSET_SEARCH_TYPES}
                                        layoutContext={layoutContext}
                                        placeholder={t('plan-download.search')}
                                        onItemSelected={onItemSelected}
                                        onBlur={() => onCommitField('asset')}
                                        hasError={hasErrors(state.committedFields, errors, 'asset')}
                                        value={selectedDropdownValue}
                                        getName={getName}
                                    />
                                </div>
                            }
                            errors={getVisibleErrorsByProp(
                                state.committedFields,
                                errors,
                                'asset',
                            ).map((error) => t(`plan-download.${error}`))}
                            help={loading && <Spinner />}
                        />
                        <FieldLayout
                            value={
                                <div className={styles['plan-download-popup__area-grid']}>
                                    <label
                                        className={labelClasses(
                                            hasEndBeforeStartError ||
                                                hasErrors(
                                                    state.committedFields,
                                                    errors,
                                                    'startTrackMeter',
                                                ),
                                        )}>
                                        {t('plan-download.start-km')}
                                    </label>
                                    <Dropdown
                                        qa-id="plan-download-start-km"
                                        disabled={disabled}
                                        options={kmPostsOnTrackNumber}
                                        value={state.areaSelection.startTrackMeter}
                                        canUnselect={true}
                                        onChange={(e) => updateProp('startTrackMeter', e)}
                                        onBlur={() => onCommitField('startTrackMeter')}
                                        hasError={
                                            hasEndBeforeStartError ||
                                            hasErrors(
                                                state.committedFields,
                                                errors,
                                                'startTrackMeter',
                                            )
                                        }
                                        wide
                                    />
                                </div>
                            }
                            errors={getVisibleErrorsByProp(
                                state.committedFields,
                                errors,
                                'startTrackMeter',
                            ).map((error) => t(`plan-download.${error}`))}
                            warnings={getVisibleIssuesAndParamsByProp(
                                state.committedFields,
                                warnings,
                                'startTrackMeter',
                            ).map((error) => t(`plan-download.${error.reason}`, error.params))}
                        />
                        <FieldLayout
                            value={
                                <div className={styles['plan-download-popup__area-grid']}>
                                    <label
                                        className={labelClasses(
                                            hasErrors(
                                                state.committedFields,
                                                errors,
                                                'endTrackMeter',
                                            ),
                                        )}>
                                        {t('plan-download.end-km')}
                                    </label>
                                    <Dropdown
                                        qa-id="plan-download-end-km"
                                        disabled={disabled}
                                        options={kmPostsOnTrackNumber}
                                        value={state.areaSelection.endTrackMeter}
                                        canUnselect={true}
                                        onChange={(e) => updateProp('endTrackMeter', e)}
                                        onBlur={() => onCommitField('endTrackMeter')}
                                        hasError={hasErrors(
                                            state.committedFields,
                                            errors,
                                            'endTrackMeter',
                                        )}
                                        wide
                                    />
                                </div>
                            }
                            errors={getVisibleErrorsByProp(
                                state.committedFields,
                                errors,
                                'endTrackMeter',
                            ).map((error) => t(`plan-download.${error}`))}
                            warnings={getVisibleIssuesAndParamsByProp(
                                state.committedFields,
                                warnings,
                                'endTrackMeter',
                            ).map((error) => t(`plan-download.${error.reason}`, error.params))}
                        />
                    </React.Fragment>
                )}
            </div>
            <div className={styles['plan-download-popup__radio-container']}>
                <Radio
                    checked={areaSelectionType === 'MAINTENANCE_AREA'}
                    onChange={() => setAreaSelectionType('MAINTENANCE_AREA')}
                    disabled>
                    {t('plan-download.maintenance-area')}
                </Radio>
            </div>
            <div className={styles['plan-download-popup__radio-container']}>
                <Radio
                    checked={areaSelectionType === 'MAP_AREA'}
                    onChange={() => setAreaSelectionType('MAP_AREA')}
                    disabled>
                    {t('plan-download.choose-from-map')}
                </Radio>
            </div>
        </React.Fragment>
    );
};
