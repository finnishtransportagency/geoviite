import React from 'react';
import { useTranslation } from 'react-i18next';
import { ValidationError, ValidationErrorType } from 'utils/validation-utils';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { createClassName } from 'vayla-design-lib/utils';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { DescriptionSuffixDropdown } from 'tool-panel/location-track/description-suffix-dropdown';
import {
    AddressPoint,
    LayoutLocationTrack,
    LayoutSwitchId,
    LocationTrackDuplicate,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import {
    FirstSplitTargetCandidate,
    SplitTargetCandidate,
    SwitchOnLocationTrack,
} from 'tool-panel/location-track/split-store';
import {
    calculateBoundingBoxToShowAroundLocation,
    MAP_POINT_NEAR_BBOX_OFFSET,
} from 'map/map-utils';
import NavigableTrackMeter from 'geoviite-design-lib/track-meter/navigable-track-meter';
import { isEqualIgnoreCase } from 'utils/string-utils';
import {
    END_SWITCH_NOT_MATCHING_ERROR,
    getOperation,
    START_SWITCH_NOT_MATCHING_ERROR,
} from './split-utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { BoundingBox, Point } from 'model/geometry';

type CommonProps = {
    addressPoint: AddressPoint | undefined;
    editingDisabled: boolean;
    showArea: (bbox: BoundingBox) => void;
};

type EndpointProps = CommonProps & {
    splitSwitch: SwitchOnLocationTrack | undefined;
    onSwitchClick: () => void;
};

type SplitProps = CommonProps & {
    locationTrackId: LocationTrackId;
    split: SplitTargetCandidate | FirstSplitTargetCandidate;
    onRemove?: (switchId: LayoutSwitchId) => void;
    updateSplit: (updateSplit: SplitTargetCandidate | FirstSplitTargetCandidate) => void;
    duplicateTrackId: LocationTrackId | undefined;
    nameErrors: ValidationError<SplitTargetCandidate>[];
    descriptionErrors: ValidationError<SplitTargetCandidate>[];
    switchErrors: ValidationError<SplitTargetCandidate>[];
    nameRef: React.RefObject<HTMLInputElement>;
    descriptionBaseRef: React.RefObject<HTMLInputElement>;
    deletingDisabled: boolean;
    allDuplicateLocationTracks: LocationTrackDuplicate[];
    duplicateLocationTrack: LayoutLocationTrack | undefined;
    underlyingAssetExists: boolean;
    showArea: (bbox: BoundingBox) => void;
    onSplitTrackClicked: () => void;
    onFocus: () => void;
    onBlur: () => void;
    onHighlight: () => void;
    onReleaseHighlight: () => void;
    onHighlightSwitch: () => void;
    onReleaseSwitchHighlight: () => void;
};

export function getShowSwitchOnMapBoundingBox(location: Point): BoundingBox {
    return calculateBoundingBoxToShowAroundLocation(location, 100);
}

export const LocationTrackSplittingEndpoint: React.FC<EndpointProps> = ({
    splitSwitch,
    addressPoint,
    editingDisabled,
    onSwitchClick,
}) => {
    // const { t } = useTranslation();
    return (
        <div
            className={createClassName(
                styles['location-track-infobox__split-container'],
                styles['location-track-infobox__split-container--endpoint'],
            )}>
            <div className="location-track-infobox__split-item-ball-container">
                <div
                    className={createClassName(
                        styles['location-track-infobox__split-item-ball'],
                        editingDisabled &&
                            styles['location-track-infobox__split-item-ball--disabled'],
                    )}
                    onClick={onSwitchClick}
                />
            </div>
            <div className={styles['location-track-infobox__split-switch-row']}>
                <span
                    className={styles['location-track-infobox__split-switch-name']}
                    onClick={onSwitchClick}>
                    {splitSwitch?.name}
                </span>
                <span className={styles['location-track-infobox__split-switch-row-fill']}></span>
                <span className={styles['location-track-infobox__split-track-address']}>
                    <NavigableTrackMeter
                        trackMeter={addressPoint?.address}
                        location={addressPoint?.point}
                        mapNavigationBboxOffset={MAP_POINT_NEAR_BBOX_OFFSET}
                    />
                </span>
                <div
                    className={createClassName(
                        styles['location-track-infobox__split-close-button'],
                        styles['location-track-infobox__split-close-button--disabled'],
                    )}></div>
            </div>
        </div>
    );
};

export const LocationTrackSplit: React.FC<SplitProps> = ({
    locationTrackId,
    split,
    addressPoint,
    onRemove,
    updateSplit,
    duplicateTrackId,
    nameErrors,
    descriptionErrors,
    switchErrors,
    editingDisabled,
    deletingDisabled,
    nameRef,
    descriptionBaseRef,
    allDuplicateLocationTracks,
    duplicateLocationTrack,
    underlyingAssetExists,
    showArea,
    onSplitTrackClicked,
    onFocus,
    onBlur,
    onHighlight,
    onReleaseHighlight,
    onHighlightSwitch,
    onReleaseSwitchHighlight,
}) => {
    const { t } = useTranslation();
    const switchId = split.type === 'SPLIT' ? split.switch.switchId : undefined;
    const [nameCommitted, setNameCommitted] = React.useState(split.name !== '');
    const [descriptionCommitted, setDescriptionCommitted] = React.useState(
        split.descriptionBase !== '',
    );
    const startSwitchMatchingError = switchErrors.find(
        (error) => error.reason == START_SWITCH_NOT_MATCHING_ERROR,
    );
    const endSwitchMatchingError = switchErrors.find(
        (error) => error.reason == END_SWITCH_NOT_MATCHING_ERROR,
    );

    // TODO: Adding any kind of dependency array causes infinite re-render loops, find out why
    React.useEffect(() => {
        if (!nameErrors.length) {
            setNameCommitted(true);
        }
        if (!descriptionErrors.length) {
            setDescriptionCommitted(true);
        }
    });

    const nameErrorsVisible = nameCommitted && nameErrors.length > 0;
    const descriptionErrorsVisible = descriptionCommitted && descriptionErrors.length > 0;

    function showSwitchOnMap(location: Point) {
        showArea(getShowSwitchOnMapBoundingBox(location));
    }

    return (
        <div
            className={styles['location-track-infobox__split-container']}
            onMouseEnter={onHighlight}
            onMouseLeave={onReleaseHighlight}>
            <div
                className={createClassName(
                    styles['location-track-infobox__split-item-line-container'],
                )}
                onClick={onSplitTrackClicked}>
                <div
                    className={createClassName(
                        styles['location-track-infobox__split-item-line'],
                        !underlyingAssetExists &&
                            styles['location-track-infobox__split-item-line--disabled'],
                    )}
                />
            </div>
            <div
                className={styles['location-track-infobox__split-item-ball-container']}
                onClick={() => addressPoint && showSwitchOnMap(addressPoint.point)}>
                <div
                    className={createClassName(
                        styles['location-track-infobox__split-item-ball'],
                        !underlyingAssetExists &&
                            styles['location-track-infobox__split-item-ball--disabled'],
                    )}
                />
            </div>
            <div className={styles['location-track-infobox__split-fields-container']}>
                <div>
                    <div
                        className={styles['location-track-infobox__split-switch-row']}
                        onMouseEnter={onHighlightSwitch}
                        onMouseLeave={onReleaseSwitchHighlight}>
                        <span
                            className={styles['location-track-infobox__split-switch-name']}
                            onClick={() => addressPoint && showSwitchOnMap(addressPoint.point)}>
                            {split.switch.name}
                        </span>
                        <span
                            className={
                                styles['location-track-infobox__split-switch-row-fill']
                            }></span>
                        <span className={styles['location-track-infobox__split-track-address']}>
                            <NavigableTrackMeter
                                trackMeter={addressPoint?.address}
                                location={addressPoint?.point}
                                mapNavigationBboxOffset={MAP_POINT_NEAR_BBOX_OFFSET}
                            />
                        </span>
                        <div
                            className={createClassName(
                                styles['location-track-infobox__split-close-button'],
                                deletingDisabled
                                    ? styles['location-track-infobox__split-close-button--disabled']
                                    : styles['location-track-infobox__split-close-button--enabled'],
                            )}
                            onClick={() =>
                                onRemove && !deletingDisabled && switchId && onRemove(switchId)
                            }>
                            {onRemove && (
                                <Icons.Clear size={IconSize.SMALL} color={IconColor.INHERIT} />
                            )}
                        </div>
                    </div>
                    {startSwitchMatchingError && (
                        <div
                            className={createClassName(
                                styles['location-track-infobox__split-switch-error-msg'],
                                styles['location-track-infobox__split-switch-error-msg--start'],
                            )}>
                            {t(
                                `tool-panel.location-track.splitting.validation.${startSwitchMatchingError.reason}`,
                                startSwitchMatchingError.params,
                            )}
                        </div>
                    )}
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        label={t('tool-panel.location-track.track-name')}
                        hasErrors={nameErrorsVisible}>
                        <TextField
                            ref={nameRef}
                            value={split.name}
                            hasError={nameErrorsVisible}
                            disabled={editingDisabled}
                            onChange={(e) => {
                                const duplicate = allDuplicateLocationTracks.find((lt) =>
                                    isEqualIgnoreCase(lt.name, e.target.value),
                                );

                                if (duplicate && nameRef.current) {
                                    nameRef.current.value = duplicate.name;
                                }

                                updateSplit({
                                    ...split,
                                    name: e.target.value,
                                    duplicateTrackId: duplicate?.id,
                                    duplicateStatus: duplicate?.duplicateStatus,
                                    operation: getOperation(
                                        locationTrackId,
                                        split.switch.switchId,
                                        duplicate?.duplicateStatus,
                                    ),
                                    hasAutogeneratedName: false,
                                });
                            }}
                            onFocus={onFocus}
                            onBlur={() => {
                                setNameCommitted(true);
                                onBlur();
                            }}
                        />
                    </InfoboxField>
                    {(nameErrorsVisible || split.operation) && (
                        <InfoboxField
                            className={createClassName(
                                styles['location-track-infobox__split-remark'],
                            )}
                            hasErrors={nameErrorsVisible}
                            label={''}>
                            {!nameErrorsVisible && split.operation && (
                                <InfoboxText
                                    value={t(
                                        `tool-panel.location-track.splitting.operation.${split.operation}`,
                                    )}
                                />
                            )}
                            {nameErrorsVisible &&
                                nameErrors.map((error, i) => (
                                    <SplitErrorMessage key={i.toString()} error={error} />
                                ))}
                        </InfoboxField>
                    )}
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        hasErrors={descriptionErrorsVisible}
                        label={t('tool-panel.location-track.splitting.description-base')}>
                        <TextField
                            value={
                                duplicateLocationTrack
                                    ? duplicateLocationTrack.descriptionBase
                                    : split.descriptionBase
                            }
                            hasError={descriptionErrorsVisible}
                            disabled={editingDisabled || !!duplicateTrackId}
                            onChange={(e) => {
                                updateSplit({
                                    ...split,
                                    descriptionBase: e.target.value,
                                    hasAutogeneratedName: false,
                                });
                            }}
                            onFocus={onFocus}
                            onBlur={() => {
                                setDescriptionCommitted(true);
                                onBlur();
                            }}
                            ref={descriptionBaseRef}
                        />
                    </InfoboxField>
                    {descriptionErrorsVisible && (
                        <InfoboxField
                            className={createClassName(
                                styles['location-track-infobox__split-remark'],
                            )}
                            hasErrors={descriptionErrorsVisible}
                            label={''}>
                            {descriptionErrorsVisible &&
                                descriptionErrors.map((error, index) => (
                                    <InfoboxText
                                        value={t(
                                            `tool-panel.location-track.splitting.validation.${error.reason}`,
                                        )}
                                        key={index.toString()}
                                    />
                                ))}
                        </InfoboxField>
                    )}
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        label={t('tool-panel.location-track.splitting.description-suffix')}>
                        <DescriptionSuffixDropdown
                            suffixMode={
                                duplicateLocationTrack
                                    ? duplicateLocationTrack.descriptionSuffix
                                    : split.suffixMode
                            }
                            onChange={(mode) => {
                                updateSplit({ ...split, suffixMode: mode });
                            }}
                            onBlur={onBlur}
                            disabled={editingDisabled || !!duplicateTrackId}
                        />
                    </InfoboxField>

                    {endSwitchMatchingError && (
                        <div
                            className={createClassName(
                                styles['location-track-infobox__split-switch-error-msg'],
                                styles['location-track-infobox__split-switch-error-msg--end'],
                                endSwitchMatchingError.type == ValidationErrorType.ERROR &&
                                    styles['location-track-infobox__split-switch-error-msg--error'],
                            )}>
                            {t(
                                `tool-panel.location-track.splitting.validation.${endSwitchMatchingError.reason}`,
                                endSwitchMatchingError.params,
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

type SplitErrorMessageProps = {
    error: ValidationError<SplitTargetCandidate>;
};
const SplitErrorMessage: React.FC<SplitErrorMessageProps> = ({ error }) => {
    const { t } = useTranslation();
    const style =
        error.type === ValidationErrorType.ERROR
            ? 'location-track-infobox__split-error'
            : 'location-track-infobox__split-warning';
    return (
        <InfoboxText
            className={styles[style]}
            value={t(
                `tool-panel.location-track.splitting.validation.${error.reason}`,
                error.params,
            )}
        />
    );
};
