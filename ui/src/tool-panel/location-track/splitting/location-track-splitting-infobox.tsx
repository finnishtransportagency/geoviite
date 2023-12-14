import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import Infobox from 'tool-panel/infobox/infobox';
import { LocationTrackInfoboxVisibilities } from 'track-layout/track-layout-slice';
import {
    AddressPoint,
    AlignmentStartAndEnd,
    LayoutLocationTrack,
    LayoutSwitchId,
    LocationTrackDuplicate,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import {
    InitialSplit,
    sortSplitsByDistance,
    Split,
    SwitchOnLocationTrack,
} from 'tool-panel/location-track/split-store';
import {
    useConflictingTracks,
    useLocationTrack,
    useLocationTrackStartAndEnd,
} from 'track-layout/track-layout-react-utils';
import { ValidationError, ValidationErrorType } from 'utils/validation-utils';
import {
    LocationTrackSplit,
    LocationTrackSplittingEndpoint,
} from 'tool-panel/location-track/splitting/location-track-split';
import { filterNotEmpty } from 'utils/array-utils';
import { BoundingBox } from 'model/geometry';
import {
    validateLocationTrackDescriptionBase,
    validateLocationTrackName,
} from 'tool-panel/location-track/dialog/location-track-validation';

type LocationTrackSplittingInfoboxContainerProps = {
    duplicateLocationTracks: LocationTrackDuplicate[];
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    initialSplit: InitialSplit;
    splits: Split[];
    allowedSwitches: SwitchOnLocationTrack[];
    removeSplit: (switchId: LayoutSwitchId) => void;
    locationTrackId: string;
    cancelSplitting: () => void;
    updateSplit: (updatedSplit: Split | InitialSplit) => void;
    showArea: (boundingBox: BoundingBox) => void;
};

type LocationTrackSplittingInfoboxProps = {
    duplicateLocationTracks: LocationTrackDuplicate[];
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    initialSplit: InitialSplit;
    splits: Split[];
    allowedSwitches: SwitchOnLocationTrack[];
    removeSplit: (switchId: LayoutSwitchId) => void;
    locationTrack: LayoutLocationTrack;
    conflictingLocationTracks: string[];
    startAndEnd: AlignmentStartAndEnd;
    cancelSplitting: () => void;
    updateSplit: (updatedSplit: Split | InitialSplit) => void;
    showArea: (boundingBox: BoundingBox) => void;
};

const validateSplitName = (
    splitName: string,
    allSplitNames: string[],
    conflictingTrackNames: string[],
) => {
    const errors: ValidationError<Split>[] = validateLocationTrackName(splitName);

    if (
        allSplitNames.filter((s) => s !== '' && s.toLowerCase() === splitName.toLowerCase())
            .length > 1
    )
        errors.push({
            field: 'name',
            reason: 'conflicts-with-split',
            type: ValidationErrorType.ERROR,
        });
    if (conflictingTrackNames.map((t) => t.toLowerCase()).includes(splitName.toLowerCase())) {
        errors.push({
            field: 'name',
            reason: 'conflicts-with-track',
            type: ValidationErrorType.ERROR,
        });
    }
    return errors;
};

const validateSplitDescription = (
    description: string,
    duplicateOf: LocationTrackId | undefined,
) => {
    const errors: ValidationError<Split>[] = validateLocationTrackDescriptionBase(description);
    if (!duplicateOf && description === '')
        errors.push({
            field: 'descriptionBase',
            reason: 'required',
            type: ValidationErrorType.ERROR,
        });
    return errors;
};

export const LocationTrackSplittingInfoboxContainer: React.FC<
    LocationTrackSplittingInfoboxContainerProps
> = ({
    locationTrackId,
    initialSplit,
    splits,
    duplicateLocationTracks,
    visibilities,
    visibilityChange,
    allowedSwitches,
    removeSplit,
    cancelSplitting,
    updateSplit,
    showArea,
}) => {
    const locationTrack = useLocationTrack(locationTrackId, 'DRAFT');
    const [startAndEnd, _] = useLocationTrackStartAndEnd(locationTrackId, 'DRAFT');
    const conflictingTracks = useConflictingTracks(
        locationTrack?.trackNumberId,
        [initialSplit, ...splits].map((s) => s.name),
        [initialSplit, ...splits].map((s) => s.duplicateOf).filter(filterNotEmpty),
        'DRAFT',
    );

    return (
        locationTrack &&
        startAndEnd && (
            <LocationTrackSplittingInfobox
                duplicateLocationTracks={duplicateLocationTracks}
                visibilities={visibilities}
                visibilityChange={visibilityChange}
                initialSplit={initialSplit}
                splits={splits}
                allowedSwitches={allowedSwitches}
                removeSplit={removeSplit}
                cancelSplitting={cancelSplitting}
                updateSplit={updateSplit}
                startAndEnd={startAndEnd}
                conflictingLocationTracks={conflictingTracks?.map((t) => t.name) || []}
                locationTrack={locationTrack}
                showArea={showArea}
            />
        )
    );
};

export const LocationTrackSplittingInfobox: React.FC<LocationTrackSplittingInfoboxProps> = ({
    duplicateLocationTracks,
    visibilities,
    visibilityChange,
    initialSplit,
    splits,
    allowedSwitches,
    removeSplit,
    cancelSplitting,
    updateSplit,
    startAndEnd,
    conflictingLocationTracks,
    locationTrack,
    showArea,
}) => {
    const { t } = useTranslation();
    const getSplitAddressPoint = (split: Split): AddressPoint | undefined => {
        const switchAtSplit = allowedSwitches.find((s) => s.switchId === split.switchId);

        if (switchAtSplit?.location && switchAtSplit?.address) {
            return {
                point: { ...switchAtSplit.location, m: -1 },
                address: switchAtSplit.address,
            };
        }

        return undefined;
    };

    const sortedSplits = sortSplitsByDistance(splits);

    const allSplitNames = [initialSplit, ...splits].map((s) => s.name);

    const initialSplitValidated = {
        split: initialSplit,
        nameErrors: validateSplitName(initialSplit.name, allSplitNames, conflictingLocationTracks),
        descriptionErrors: validateSplitDescription(
            initialSplit.descriptionBase,
            initialSplit.duplicateOf,
        ),
    };
    const splitsValidated = sortedSplits.map((s) => ({
        split: s,
        nameErrors: validateSplitName(s.name, allSplitNames, conflictingLocationTracks),
        descriptionErrors: validateSplitDescription(s.descriptionBase, s.duplicateOf),
    }));

    const anyErrors =
        initialSplitValidated.nameErrors.length > 0 ||
        initialSplitValidated.descriptionErrors.length > 0 ||
        splitsValidated.some((s) => s.nameErrors.length > 0 || s.descriptionErrors.length > 0);

    return (
        <React.Fragment>
            {startAndEnd?.start && startAndEnd?.end && locationTrack && (
                <Infobox
                    contentVisible={visibilities.splitting}
                    onContentVisibilityChange={() => visibilityChange('splitting')}
                    title={t('tool-panel.location-track.splitting.title')}>
                    <InfoboxContent className={styles['location-track-infobox__split']}>
                        <LocationTrackSplit
                            split={initialSplit}
                            addressPoint={startAndEnd?.start}
                            duplicateLocationTracks={duplicateLocationTracks}
                            updateSplit={updateSplit}
                            duplicateOf={initialSplit.duplicateOf}
                            nameErrors={initialSplitValidated.nameErrors}
                            descriptionErrors={initialSplitValidated.descriptionErrors}
                            showArea={showArea}
                        />
                        {splitsValidated.map(({ split, nameErrors, descriptionErrors }) => {
                            return (
                                <LocationTrackSplit
                                    key={`${split.location.x}_${split.location.y}`}
                                    split={split}
                                    addressPoint={getSplitAddressPoint(split)}
                                    onRemove={removeSplit}
                                    duplicateLocationTracks={duplicateLocationTracks}
                                    updateSplit={updateSplit}
                                    duplicateOf={split.duplicateOf}
                                    nameErrors={nameErrors}
                                    descriptionErrors={descriptionErrors}
                                    showArea={showArea}
                                />
                            );
                        })}
                        <LocationTrackSplittingEndpoint
                            addressPoint={startAndEnd.end}
                            showArea={showArea}
                        />
                        {splits.length === 0 && (
                            <InfoboxContentSpread>
                                <MessageBox>
                                    {t('tool-panel.location-track.splitting.splitting-guide')}
                                </MessageBox>
                            </InfoboxContentSpread>
                        )}
                        <InfoboxButtons>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                size={ButtonSize.SMALL}
                                onClick={cancelSplitting}>
                                {t('button.cancel')}
                            </Button>
                            <Button size={ButtonSize.SMALL} disabled={anyErrors}>
                                {t('tool-panel.location-track.splitting.confirm-split')}
                            </Button>
                        </InfoboxButtons>
                    </InfoboxContent>
                </Infobox>
            )}
        </React.Fragment>
    );
};
