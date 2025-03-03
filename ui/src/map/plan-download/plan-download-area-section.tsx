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
import { AreaSelection, PlanDownloadState } from 'map/plan-download/plan-download-slice';
import { getVisibleErrorsByProp, hasErrors, PropEdit } from 'utils/validation-utils';
import { createClassName } from 'vayla-design-lib/utils';
import styles from 'map/plan-download/plan-download-popup.scss';
import { Radio } from 'vayla-design-lib/radio/radio';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';

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
}> = ({ layoutContext, locationTrack, trackNumber, state, onUpdateProp, onCommitField }) => {
    const { t } = useTranslation();
    const [areaSelectionType, setAreaSelectionType] =
        React.useState<AreaSelectionType>('TRACK_METERS');

    function updateProp<TKey extends keyof AreaSelection>(key: TKey, value: AreaSelection[TKey]) {
        onUpdateProp({
            key: key,
            value: value,
            editingExistingValue: false,
        });
    }

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
        createClassName(
            styles['plan-download-popup__field-label'],
            hasErrors && styles['plan-download-popup__field-label--error'],
        );

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

    return (
        <div>
            <div>
                <div className={styles['plan-download-popup__radio-container']}>
                    <Radio
                        checked={areaSelectionType === 'TRACK_METERS'}
                        onChange={() => setAreaSelectionType('TRACK_METERS')}>
                        {t('plan-download.track-meter-range')}
                    </Radio>
                </div>
                {areaSelectionType === 'TRACK_METERS' && (
                    <div className={styles['plan-download-popup__area-grid']}>
                        <label
                            className={labelClasses(
                                hasErrors(
                                    state.committedFields,
                                    state.validationIssues,
                                    'trackNumber',
                                ),
                            )}>
                            {t('plan-download.track-number-or-location-track')}
                        </label>
                        <FieldLayout
                            value={
                                <SearchDropdown
                                    searchTypes={ASSET_SEARCH_TYPES}
                                    layoutContext={layoutContext}
                                    placeholder={'Hae tunnuksella'}
                                    onItemSelected={onItemSelected}
                                    onBlur={() => onCommitField('trackNumber')}
                                    hasError={hasErrors(
                                        state.committedFields,
                                        state.validationIssues,
                                        'trackNumber',
                                    )}
                                    value={value}
                                    getName={getName}
                                />
                            }
                            errors={getVisibleErrorsByProp(
                                state.committedFields,
                                state.validationIssues,
                                'trackNumber',
                            ).map((error) => t(`plan-download.${error}`))}
                        />
                        <label
                            className={labelClasses(
                                hasEndBeforeStartError ||
                                    hasErrors(
                                        state.committedFields,
                                        state.validationIssues,
                                        'startTrackMeter',
                                    ),
                            )}>
                            {t('data-products.search.track-address-start')}
                        </label>
                        <FieldLayout
                            value={
                                <TextField
                                    qa-id="data-products-search-start-km"
                                    value={state.areaSelection.startTrackMeter}
                                    onChange={(e) => updateProp('startTrackMeter', e.target.value)}
                                    onBlur={() => onCommitField('startTrackMeter')}
                                    hasError={
                                        hasEndBeforeStartError ||
                                        hasErrors(
                                            state.committedFields,
                                            state.validationIssues,
                                            'startTrackMeter',
                                        )
                                    }
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp(
                                state.committedFields,
                                state.validationIssues,
                                'startTrackMeter',
                            ).map((error) => t(`plan-download.${error}`))}
                        />
                        <label
                            className={labelClasses(
                                hasErrors(
                                    state.committedFields,
                                    state.validationIssues,
                                    'endTrackMeter',
                                ),
                            )}>
                            {t('data-products.search.track-address-end')}
                        </label>
                        <FieldLayout
                            value={
                                <TextField
                                    qa-id="data-products-search-end-km"
                                    value={state.areaSelection.endTrackMeter}
                                    onChange={(e) => updateProp('endTrackMeter', e.target.value)}
                                    onBlur={() => onCommitField('endTrackMeter')}
                                    hasError={hasErrors(
                                        state.committedFields,
                                        state.validationIssues,
                                        'endTrackMeter',
                                    )}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp(
                                state.committedFields,
                                state.validationIssues,
                                'endTrackMeter',
                            ).map((error) => t(`plan-download.${error}`))}
                        />
                    </div>
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
        </div>
    );
};
