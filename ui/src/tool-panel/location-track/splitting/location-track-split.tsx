import React from 'react';
import { useTranslation } from 'react-i18next';
import { FieldValidationIssue, FieldValidationIssueType } from 'utils/validation-utils';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { createClassName } from 'vayla-design-lib/utils';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { DescriptionSuffixDropdown } from 'tool-panel/location-track/description-suffix-dropdown';
import {
    AlignmentEndPoint,
    LocationTrackId,
    LocationTrackNameSpecifier,
    LocationTrackNamingScheme,
    SplitPoint,
} from 'track-layout/track-layout-model';
import {
    FirstSplitTargetCandidate,
    PARTIAL_DUPLICATE_EXPECTED_MINIMUM_NON_OVERLAPPING_PART_LENGTH_METERS,
    SplitTargetCandidate,
    SplitTargetId,
    SplitTargetOperation,
} from 'tool-panel/location-track/split-store';
import { calculateBoundingBoxToShowAroundLocation, MAP_POINT_NEAR_BBOX_OFFSET, } from 'map/map-utils';
import NavigableTrackMeter from 'geoviite-design-lib/track-meter/navigable-track-meter';
import { END_SPLIT_POINT_NOT_MATCHING_ERROR, START_SPLIT_POINT_NOT_MATCHING_ERROR, } from './split-utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { BoundingBox, Point } from 'model/geometry';
import { SplitDuplicateTrack } from 'track-layout/layout-location-track-api';
import { filterNotEmpty } from 'utils/array-utils';
import { RemovalConfirmationMenu } from 'tool-panel/location-track/splitting/removal-confirmation-menu';
import { useCloneRef } from 'utils/react-utils';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { locationTrackNameSpecifiers, locationTrackNamingSchemesFiltered, } from 'utils/enum-localization-utils';

type CommonProps = {
    editingDisabled: boolean;
    showArea: (bbox: BoundingBox) => void;
};

type EndpointProps = CommonProps & {
    addressPoint: AlignmentEndPoint | undefined;
    splitPoint: SplitPoint | undefined;
    onSplitPointClick: () => void;
};

type SplitProps = CommonProps & {
    locationTrackId: LocationTrackId;
    split: SplitTargetCandidate | FirstSplitTargetCandidate;
    onRemove?: (splitPoint: SplitPoint) => void;
    updateSplit: (updateSplit: SplitTargetCandidate | FirstSplitTargetCandidate) => void;
    duplicateTrackId: LocationTrackId | undefined;
    nameIssues: FieldValidationIssue<SplitTargetCandidate>[];
    descriptionIssues: FieldValidationIssue<SplitTargetCandidate>[];
    switchIssues: FieldValidationIssue<SplitTargetCandidate>[];
    setNameRef: (key: SplitTargetId, value: HTMLInputElement | null) => void;
    setDescriptionBaseRef: (key: SplitTargetId, value: HTMLInputElement | null) => void;
    deletingDisabled: boolean;
    allDuplicateLocationTracks: SplitDuplicateTrack[];
    underlyingAssetExists: boolean;
    showArea: (bbox: BoundingBox) => void;
    onSplitTrackClicked: (id: SplitTargetId) => void;
    setFocusedSplit: (id: undefined | SplitTargetId) => void;
    setHighlightedSplit: (id: undefined | SplitTargetId) => void;
    setHighlightedSplitPoint: (splitPoint: undefined | SplitPoint) => void;
};

export function getShowSwitchOnMapBoundingBox(location: Point): BoundingBox {
    return calculateBoundingBoxToShowAroundLocation(location, 100);
}

export const LocationTrackSplittingEndpoint: React.FC<EndpointProps> = ({
    splitPoint,
    addressPoint,
    editingDisabled,
    onSplitPointClick,
}) => {
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
                    onClick={onSplitPointClick}
                />
            </div>
            <div className={styles['location-track-infobox__split-switch-row']}>
                <span
                    className={styles['location-track-infobox__split-switch-name']}
                    onClick={onSplitPointClick}>
                    {splitPoint?.name}
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
                        styles['location-track-infobox__split-close-button-container'],
                        styles['location-track-infobox__split-close-button--disabled'],
                    )}></div>
            </div>
        </div>
    );
};

const LocationTrackSplitM: React.FC<SplitProps> = ({
    locationTrackId: _x,
    split,
    onRemove,
    updateSplit,
    duplicateTrackId,
    nameIssues,
    descriptionIssues,
    switchIssues,
    editingDisabled,
    deletingDisabled,
    setNameRef,
    setDescriptionBaseRef,
    allDuplicateLocationTracks,
    underlyingAssetExists,
    showArea,
    onSplitTrackClicked,
    setFocusedSplit,
    setHighlightedSplit,
    setHighlightedSplitPoint,
}) => {
    const { t } = useTranslation();
    const [nameCommitted, setNameCommitted] = React.useState(split.name !== '');
    const [descriptionCommitted, setDescriptionCommitted] = React.useState(
        split.descriptionBase !== '',
    );
    const startSwitchMatchingError = switchIssues.find(
        (error) => error.reason === START_SPLIT_POINT_NOT_MATCHING_ERROR,
    );
    const endSwitchMatchingError = switchIssues.find(
        (error) => error.reason === END_SPLIT_POINT_NOT_MATCHING_ERROR,
    );
    const duplicate = allDuplicateLocationTracks.find((d) => d.id === split.duplicateTrackId);

    React.useEffect(() => {
        if (!nameIssues.length) {
            setNameCommitted(true);
        }
    }, [nameIssues.length]);
    React.useEffect(() => {
        if (!descriptionIssues.length) {
            setDescriptionCommitted(true);
        }
    }, [descriptionIssues.length]);

    const nameRef = React.useCallback(
        (e: HTMLInputElement) => {
            setNameRef(split.id, e);
        },
        [setNameRef],
    );
    const _localNameRef = useCloneRef(nameRef);
    const descriptionBaseRef = React.useCallback(
        (e: HTMLInputElement) => {
            setDescriptionBaseRef(split.id, e);
        },
        [setDescriptionBaseRef],
    );

    const closeButtonRef = React.useRef(null);

    const nameErrorsVisible = nameCommitted && nameIssues.length > 0;
    const descriptionErrorsVisible = descriptionCommitted && descriptionIssues.length > 0;

    const isPartialDuplicate = split.duplicateStatus?.match === 'PARTIAL';
    const duplicateLength = duplicate?.length;
    const overlappingDuplicateLength = split.duplicateStatus?.overlappingLength;
    const nonOverlappingDuplicateLength =
        (duplicateLength !== undefined &&
            overlappingDuplicateLength !== undefined &&
            duplicateLength - overlappingDuplicateLength) ||
        undefined;

    const isShortNonOverlappingDuplicateLength =
        isPartialDuplicate &&
        nonOverlappingDuplicateLength !== undefined &&
        nonOverlappingDuplicateLength <
            PARTIAL_DUPLICATE_EXPECTED_MINIMUM_NON_OVERLAPPING_PART_LENGTH_METERS;

    const addressPoint = {
        point: split.splitPoint.location,
        address: split.splitPoint.address,
    };

    function getOperationTooltip(
        operation: SplitTargetOperation,
        overlappingDuplicateLength: number | undefined,
        nonOverlappingDuplicateLength: number | undefined,
        isShortNonOverlappingDuplicateLength: boolean,
    ) {
        if (operation === 'TRANSFER') {
            const isPartialTooltip = t(
                'tool-panel.location-track.splitting.is-partial-duplicate-tooltip',
                {
                    overlappingLength: overlappingDuplicateLength?.toFixed(1),
                    nonOverlappingLength: nonOverlappingDuplicateLength?.toFixed(1),
                },
            );
            const tooShortNonOverlappingLengthWarning = isShortNonOverlappingDuplicateLength
                ? t('tool-panel.location-track.splitting.short-duplicate-non-overlap-warning', {
                      overlappingLength: overlappingDuplicateLength?.toFixed(1),
                      nonOverlappingLength: nonOverlappingDuplicateLength?.toFixed(1),
                  })
                : undefined;
            return [isPartialTooltip, tooShortNonOverlappingLengthWarning]
                .filter(filterNotEmpty)
                .join('\n\n');
        } else {
            return '';
        }
    }

    const operationTooltip = getOperationTooltip(
        split.operation,
        overlappingDuplicateLength,
        nonOverlappingDuplicateLength,
        isShortNonOverlappingDuplicateLength,
    );

    function showSwitchOnMap(location: Point) {
        showArea(getShowSwitchOnMapBoundingBox(location));
    }

    const [showRemovalConfirmationMenu, setShowRemovalConfirmationMenu] = React.useState(false);
    const closeButtonClassName = () => {
        if (!onRemove) {
            return undefined;
        } else {
            return deletingDisabled
                ? styles['location-track-infobox__split-close-button--disabled']
                : styles['location-track-infobox__split-close-button--enabled'];
        }
    };

    const onHighlight = React.useCallback(() => {
        setHighlightedSplit(split.id);
    }, [setHighlightedSplit, split]);
    const onReleaseHighlight = React.useCallback(() => {
        setHighlightedSplit(undefined);
    }, [setHighlightedSplit]);
    const onHighlightSplitPoint = React.useCallback(() => {
        setHighlightedSplitPoint(split.splitPoint);
    }, [setHighlightedSplitPoint]);
    const onReleaseSwitchHighlight = React.useCallback(() => {
        setHighlightedSplitPoint(undefined);
    }, [setHighlightedSplitPoint]);
    const onFocus = React.useCallback(() => {
        setFocusedSplit(split.id);
    }, [setFocusedSplit, split]);
    const onBlur = React.useCallback(() => {
        setFocusedSplit(undefined);
    }, [setFocusedSplit]);

    const namingSchemeOptions = locationTrackNamingSchemesFiltered(
        LocationTrackNamingScheme.FREE_TEXT,
        LocationTrackNamingScheme.WITHIN_OPERATIONAL_POINT,
        LocationTrackNamingScheme.BETWEEN_OPERATIONAL_POINTS,
        LocationTrackNamingScheme.CHORD,
    );

    const showFreeTextInput =
        // Math.random() * 2 < 3 ||
        split.namingScheme === LocationTrackNamingScheme.FREE_TEXT ||
        split.namingScheme === LocationTrackNamingScheme.WITHIN_OPERATIONAL_POINT;
    const showNameSpecifierInput =
        // Math.random() * 2 < 3 ||
        split.namingScheme === LocationTrackNamingScheme.BETWEEN_OPERATIONAL_POINTS;
    const showFullName = !showFreeTextInput;

    function updateName(
        namingScheme: LocationTrackNamingScheme,
        freeText: string | undefined,
        nameSpecifier: LocationTrackNameSpecifier | undefined,
    ) {
        updateSplit({
            ...split,
            // TODO: GVT-3083 split UI doesn't support other name schemes yet
            namingScheme: namingScheme,
            nameFreeText: freeText,
            nameSpecifier: nameSpecifier,
            // TODO: GVT-3083 use formatTrackName when other schemes are possible
            //name: e.target.value,
            // duplicateTrackId: duplicate?.id,
            // duplicateStatus: duplicate?.status,
            // operation: getOperation(locationTrackId, duplicate?.status),
            hasAutogeneratedName: false,
        });
    }

    return (
        <div
            className={styles['location-track-infobox__split-container']}
            onMouseEnter={onHighlight}
            onMouseLeave={onReleaseHighlight}
            qa-id={'split-target-track-container'}>
            <div
                className={createClassName(
                    styles['location-track-infobox__split-item-line-container'],
                )}
                onClick={() => onSplitTrackClicked(split.id)}>
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
                        onMouseEnter={onHighlightSplitPoint}
                        onMouseLeave={onReleaseSwitchHighlight}>
                        <span
                            className={styles['location-track-infobox__split-switch-name']}
                            onClick={() => addressPoint && showSwitchOnMap(addressPoint.point)}>
                            {split.splitPoint.name}
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
                            ref={closeButtonRef}
                            className={createClassName(
                                styles['location-track-infobox__split-close-button-container'],
                                closeButtonClassName(),
                            )}
                            onClick={() => {
                                setShowRemovalConfirmationMenu(!showRemovalConfirmationMenu);
                            }}>
                            {onRemove && (
                                <Icons.Clear size={IconSize.SMALL} color={IconColor.INHERIT} />
                            )}
                        </div>
                        {showRemovalConfirmationMenu && onRemove && (
                            <RemovalConfirmationMenu
                                anchorElementRef={closeButtonRef}
                                onConfirmRemoval={() => onRemove(split.splitPoint)}
                                onClose={() => setShowRemovalConfirmationMenu(false)}
                                onClickOutside={() => setShowRemovalConfirmationMenu(false)}
                            />
                        )}
                    </div>
                    {startSwitchMatchingError && (
                        <div
                            className={createClassName(
                                styles['location-track-infobox__split-switch-error-msg'],
                                styles['location-track-infobox__split-switch-error-msg--start'],
                                startSwitchMatchingError.type === FieldValidationIssueType.ERROR &&
                                    styles['location-track-infobox__split-switch-error-msg--error'],
                            )}>
                            {t(
                                `tool-panel.location-track.splitting.validation.${startSwitchMatchingError.reason}`,
                                startSwitchMatchingError.params,
                            )}
                        </div>
                    )}
                    <InfoboxField
                        className={styles['location-track-infobox__split-item-field-label']}
                        label={t('tool-panel.location-track.track-naming-scheme')}>
                        <Dropdown
                            wide
                            options={namingSchemeOptions}
                            value={split.namingScheme}
                            disabled={editingDisabled}
                            onChange={(newNamingScheme) => {
                                if (newNamingScheme) {
                                    updateName(
                                        newNamingScheme,
                                        split.nameFreeText,
                                        split.nameSpecifier,
                                    );
                                }
                            }}
                            onFocus={onFocus}
                            onBlur={() => {
                                setNameCommitted(true);
                                onBlur();
                            }}
                            qa-id={'split-target-track-name'}
                        />
                    </InfoboxField>
                    {showFreeTextInput && (
                        <InfoboxField
                            className={styles['location-track-infobox__split-item-field-label']}
                            label={t('tool-panel.location-track.track-name-free-text')}
                            hasErrors={nameErrorsVisible}>
                            <TextField
                                ref={nameRef}
                                value={split.nameFreeText}
                                hasError={nameErrorsVisible}
                                disabled={editingDisabled}
                                onChange={(e) => {
                                    updateName(
                                        split.namingScheme,
                                        e.target.value,
                                        split.nameSpecifier,
                                    );
                                }}
                                onFocus={onFocus}
                                onBlur={() => {
                                    setNameCommitted(true);
                                    onBlur();
                                }}
                                qa-id={'split-target-track-name-free-text'}
                            />
                        </InfoboxField>
                    )}
                    {showNameSpecifierInput && (
                        <InfoboxField
                            className={styles['location-track-infobox__split-item-field-label']}
                            label={t('tool-panel.location-track.track-name-specifier')}>
                            <Dropdown
                                wide
                                options={locationTrackNameSpecifiers}
                                value={split.nameSpecifier}
                                disabled={editingDisabled}
                                onChange={(newNameSpecifier) => {
                                    if (newNameSpecifier) {
                                        updateName(
                                            split.namingScheme,
                                            split.nameFreeText,
                                            newNameSpecifier,
                                        );
                                    }
                                }}
                                onFocus={onFocus}
                                onBlur={() => {
                                    setNameCommitted(true);
                                    onBlur();
                                }}
                                qa-id={'split-target-track-name-specifier'}
                            />
                        </InfoboxField>
                    )}
                    {showFullName && (
                        <InfoboxField
                            className={
                                styles['location-track-infobox__split-item-field-label--low']
                            }
                            label={t('tool-panel.location-track.track-full-name')}
                            hasErrors={nameErrorsVisible}>
                            <span className={styles['location-track-infobox__split-full-name']}>
                                {split.name}
                            </span>
                        </InfoboxField>
                    )}
                    {(nameErrorsVisible || split.operation) && (
                        <InfoboxField
                            className={createClassName(
                                styles['location-track-infobox__split-remark'],
                            )}
                            hasErrors={nameErrorsVisible}
                            label={''}>
                            {!nameErrorsVisible && split.operation && (
                                <span
                                    className={createClassName(
                                        styles['location-track-infobox__split-operation'],
                                        isShortNonOverlappingDuplicateLength &&
                                            styles[
                                                'location-track-infobox__split-operation--warning'
                                            ],
                                    )}
                                    title={operationTooltip}>
                                    {t(
                                        `tool-panel.location-track.splitting.operation.${split.operation}`,
                                    )}

                                    {split.operation === 'TRANSFER' && (
                                        <span
                                            className={createClassName(
                                                styles[
                                                    'location-track-infobox__split-operation-icon'
                                                ],
                                                isShortNonOverlappingDuplicateLength &&
                                                    styles[
                                                        'location-track-infobox__split-operation-icon--warning'
                                                    ],
                                            )}>
                                            {isShortNonOverlappingDuplicateLength ? (
                                                <Icons.StatusError
                                                    size={IconSize.SMALL}
                                                    color={IconColor.INHERIT}
                                                />
                                            ) : (
                                                <Icons.Info
                                                    size={IconSize.SMALL}
                                                    color={IconColor.INHERIT}
                                                />
                                            )}
                                        </span>
                                    )}
                                </span>
                            )}
                            {nameErrorsVisible &&
                                nameIssues.map((error, i) => (
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
                                duplicate
                                    ? duplicate.descriptionStructure.base
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
                            qa-id={'split-target-track-description'}
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
                                descriptionIssues.map((error, index) => (
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
                                duplicate ? duplicate.descriptionStructure.suffix : split.suffixMode
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
                                endSwitchMatchingError.type === FieldValidationIssueType.ERROR &&
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

export const LocationTrackSplit = React.memo(LocationTrackSplitM);

type SplitErrorMessageProps = {
    error: FieldValidationIssue<SplitTargetCandidate>;
};
const SplitErrorMessage: React.FC<SplitErrorMessageProps> = ({ error }) => {
    const { t } = useTranslation();
    const style =
        error.type === FieldValidationIssueType.ERROR
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
