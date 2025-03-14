import * as React from 'react';
import { useTranslation } from 'react-i18next';
import {
    LocationTrackItemValue,
    SearchDropdown,
    SearchItemValue,
    SearchType,
    TrackNumberItemValue,
} from 'tool-bar/search-dropdown';
import { LayoutContext } from 'common/common-model';
import { LayoutLocationTrack, LayoutTrackNumber } from 'track-layout/track-layout-model';
import { AreaSelection, PlanDownloadState } from 'map/plan-download/plan-download-store';
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
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { Spinner } from 'vayla-design-lib/spinner/spinner';

type AreaSelectionType = 'TRACK_METERS' | 'MAINTENANCE_AREA' | 'MAP_AREA';
const ASSET_SEARCH_TYPES = [SearchType.TRACK_NUMBER, SearchType.LOCATION_TRACK];

export const PlanDownloadAreaSection: React.FC<{
    layoutContext: LayoutContext;
    trackNumber: LayoutTrackNumber | undefined;
    locationTrack: LayoutLocationTrack | undefined;
    state: PlanDownloadState;
    onUpdateProp: <TKey extends keyof AreaSelection>(
        propEdit: PropEdit<AreaSelection, TKey>,
    ) => void;
    onCommitField: (field: keyof AreaSelection) => void;
    loading: boolean;
    disabled: boolean;
}> = ({
    layoutContext,
    locationTrack,
    trackNumber,
    state,
    onUpdateProp,
    onCommitField,
    loading,
    disabled,
}) => {
    const { t } = useTranslation();
    const [areaSelectionType, setAreaSelectionType] =
        React.useState<AreaSelectionType>('TRACK_METERS');

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
        if (item) {
            if (item.type === 'trackNumberSearchItem') {
                updateProp('trackNumber', item.trackNumber.id);
                updateProp('locationTrack', undefined);
            } else if (item.type === 'locationTrackSearchItem') {
                updateProp('locationTrack', item.locationTrack.id);
                updateProp('trackNumber', undefined);
            }
        }
    };
    const hasEndBeforeStartError = getVisibleErrorsByProp(
        state.committedFields,
        state.validationIssues,
        'endTrackMeter',
    ).includes('end-before-start');

    const labelClasses = (hasErrors: boolean) =>
        createClassName(hasErrors && styles['plan-download-popup__field-label--error']);

    const selectedLocationTrackValue: LocationTrackItemValue | undefined = locationTrack && {
        locationTrack,
        type: 'locationTrackSearchItem',
    };
    const selectedTrackNumberValue: TrackNumberItemValue | undefined = trackNumber && {
        trackNumber,
        type: 'trackNumberSearchItem',
    };
    const value = locationTrack
        ? selectedLocationTrackValue
        : trackNumber
          ? selectedTrackNumberValue
          : undefined;

    const getName = (item: SearchItemValue) => {
        if (item.type === 'locationTrackSearchItem') return item.locationTrack.name;
        else if (item.type === 'trackNumberSearchItem') return item.trackNumber.number;
        else return '';
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
                                            hasErrors(state.committedFields, errors, 'trackNumber'),
                                        )}>
                                        {t('plan-download.track-number-or-location-track')}
                                    </label>
                                    <SearchDropdown
                                        disabled={disabled}
                                        searchTypes={ASSET_SEARCH_TYPES}
                                        layoutContext={layoutContext}
                                        placeholder={t('plan-download.search')}
                                        onItemSelected={onItemSelected}
                                        onBlur={() => onCommitField('trackNumber')}
                                        hasError={hasErrors(
                                            state.committedFields,
                                            errors,
                                            'trackNumber',
                                        )}
                                        value={value}
                                        getName={getName}
                                    />
                                </div>
                            }
                            errors={getVisibleErrorsByProp(
                                state.committedFields,
                                errors,
                                'trackNumber',
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
                                    <TextField
                                        qa-id="plan-download-start-km"
                                        disabled={disabled}
                                        value={state.areaSelection.startTrackMeter}
                                        onChange={(e) =>
                                            updateProp('startTrackMeter', e.target.value)
                                        }
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
                                    <TextField
                                        qa-id="plan-download-end-km"
                                        disabled={disabled}
                                        value={state.areaSelection.endTrackMeter}
                                        onChange={(e) =>
                                            updateProp('endTrackMeter', e.target.value)
                                        }
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
