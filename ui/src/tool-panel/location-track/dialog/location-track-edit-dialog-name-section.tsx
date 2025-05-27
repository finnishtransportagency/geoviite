import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import styles from './location-track-edit-dialog.scss';
import {
    LocationTrackEditState,
    locationTrackNameByNamingScheme,
} from 'tool-panel/location-track/dialog/location-track-edit-store';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { LocationTrackSaveRequest } from 'linking/linking-model';
import {
    locationTrackNameSpecifiers,
    locationTrackNamingSchemes,
} from 'utils/enum-localization-utils';
import {
    LayoutLocationTrack,
    LayoutTrackNumberId,
    LocationTrackId,
    LocationTrackNaming,
    LocationTrackNamingScheme,
    LocationTrackSpecifier,
} from 'track-layout/track-layout-model';
import {
    splitSwitchName,
    useLocationTrackInfoboxExtras,
    useTrackNumber,
} from 'track-layout/track-layout-react-utils';
import { LayoutContext } from 'common/common-model';
import { getChangeTimes } from 'common/change-time-api';

type LocationTrackNameFreeTextProps = {
    name: LocationTrackNaming;
    updateFreeText: (freeText: string) => void;
    onCommitFreeText: () => void;
    getNameErrors: () => string[];
};

type LocationTrackNameTrackNumberProps = {
    name: LocationTrackNaming;
    trackNumberId: LayoutTrackNumberId | undefined;
    layoutContext: LayoutContext;
    updateFreeText: (freeText: string) => void;
    onCommitFreeText: () => void;
    updateSpecifier: (specifier: LocationTrackSpecifier | undefined) => void;
    onCommitSpecifier: () => void;
    getNameErrors: () => string[];
};

type LocationTrackNameBetweenOperatingPointsProps = {
    name: LocationTrackNaming;
    locationTrack: LayoutLocationTrack | undefined;
    layoutContext: LayoutContext;
    updateSpecifier: (specifier: LocationTrackSpecifier | undefined) => void;
    onCommitSpecifier: () => void;
    getNameErrors: () => string[];
};

type LocationTrackNameChordProps = {
    locationTrack: LayoutLocationTrack | undefined;
    layoutContext: LayoutContext;
};

const LocationTrackNameFreeText: React.FC<LocationTrackNameFreeTextProps> = ({
    name,
    updateFreeText,
    onCommitFreeText,
    getNameErrors,
}) => {
    const { t } = useTranslation();

    return (
        <FieldLayout
            label={`${t('location-track-dialog.name-free-text')} *`}
            value={
                <TextField
                    qa-id="location-track-name-free-text"
                    value={name.freeText}
                    onChange={(e) => updateFreeText && updateFreeText(e.target.value)}
                    onBlur={() => onCommitFreeText && onCommitFreeText()}
                    wide
                />
            }
            errors={getNameErrors()}
        />
    );
};

const LocationTrackNameTrackNumber: React.FC<LocationTrackNameTrackNumberProps> = ({
    name,
    trackNumberId,
    layoutContext,
    updateFreeText,
    onCommitFreeText,
    updateSpecifier,
    onCommitSpecifier,
    getNameErrors,
}) => {
    const { t } = useTranslation();

    const trackNumber = useTrackNumber(trackNumberId, layoutContext);

    return (
        <React.Fragment>
            <FieldLayout
                label={`${t('location-track-dialog.name-specifier')} *`}
                value={
                    <Dropdown
                        qa-id="location-track-name-specifier"
                        value={name.specifier}
                        options={locationTrackNameSpecifiers}
                        canUnselect={true}
                        onChange={(e) => updateSpecifier && updateSpecifier(e)}
                        onBlur={() => onCommitSpecifier && onCommitSpecifier()}
                        wide
                    />
                }
                errors={getNameErrors()}
            />
            <FieldLayout
                label={`${t('location-track-dialog.operating-point-range')} *`}
                value={
                    <TextField
                        qa-id="location-track-name-operating-point-range"
                        value={name.freeText}
                        onChange={(e) => updateFreeText && updateFreeText(e.target.value)}
                        onBlur={() => onCommitFreeText && onCommitFreeText()}
                        wide
                    />
                }
                errors={getNameErrors()}
            />
            <FieldLayout
                label={`${t('location-track-dialog.full-name')}`}
                value={`${trackNumber?.number ?? ''} ${name.specifier ?? ''} ${name.freeText}`}
            />
        </React.Fragment>
    );
};

const LocationTrackNameBetweenOperatingPoints: React.FC<
    LocationTrackNameBetweenOperatingPointsProps
> = ({ name, locationTrack, layoutContext, updateSpecifier, onCommitSpecifier, getNameErrors }) => {
    const { t } = useTranslation();
    const [extraInfo] = useLocationTrackInfoboxExtras(
        locationTrack?.id,
        layoutContext,
        getChangeTimes(),
    );

    return (
        <React.Fragment>
            <FieldLayout
                label={`${t('location-track-dialog.name-specifier')} *`}
                value={
                    <Dropdown
                        qa-id="location-track-name-specifier"
                        value={name.specifier}
                        options={locationTrackNameSpecifiers}
                        canUnselect={true}
                        onChange={(e) => updateSpecifier && updateSpecifier(e)}
                        onBlur={() => onCommitSpecifier && onCommitSpecifier()}
                        wide
                    />
                }
                errors={getNameErrors()}
            />
            <FieldLayout
                label={`${t('location-track-dialog.full-name')}`}
                value={`${name.specifier} ${extraInfo?.switchAtStart?.name}-${extraInfo?.switchAtEnd?.name}`}
            />
        </React.Fragment>
    );
};

const LocationTrackNameChord: React.FC<LocationTrackNameChordProps> = ({
    locationTrack,
    layoutContext,
}) => {
    const { t } = useTranslation();
    const [extraInfo] = useLocationTrackInfoboxExtras(
        locationTrack?.id,
        layoutContext,
        getChangeTimes(),
    );

    const [firstOperatingPoint, firstSwitchName] = splitSwitchName(extraInfo?.switchAtStart?.name);
    const [secondOperatingPoint, secondSwitchName] = splitSwitchName(extraInfo?.switchAtEnd?.name);

    const name =
        firstOperatingPoint === secondOperatingPoint
            ? `${firstOperatingPoint} ${firstSwitchName}-${secondSwitchName}`
            : `${extraInfo?.switchAtStart?.name}-${extraInfo?.switchAtEnd?.name}`;

    return <FieldLayout label={`${t('location-track-dialog.full-name')}`} value={name} />;
};

export const LocationTrackNameParts: React.FC<{
    name: LocationTrackNaming;
    locationTrack: LayoutLocationTrack | undefined;
    layoutContext: LayoutContext;
    updateProp: <TKey extends keyof LocationTrackSaveRequest>(
        key: TKey,
        value: LocationTrackSaveRequest[TKey],
    ) => void;
    onCommitField: (prop: keyof LocationTrackSaveRequest) => void;
    getVisibleErrorsByProp: (prop: keyof LocationTrackSaveRequest) => string[];
}> = ({
    name,
    locationTrack,
    layoutContext,
    updateProp,
    onCommitField,
    getVisibleErrorsByProp,
}) => {
    switch (name.scheme) {
        case LocationTrackNamingScheme.UNDEFINED:
            return (
                <LocationTrackNameFreeText
                    name={name}
                    updateFreeText={(value) => {
                        updateProp('namingScheme', { ...name, freeText: value });
                    }}
                    onCommitFreeText={() => onCommitField('namingScheme')}
                    getNameErrors={() => getVisibleErrorsByProp('namingScheme')}
                />
            );
        case LocationTrackNamingScheme.TRACK_NUMBER_TRACK:
            return (
                <LocationTrackNameTrackNumber
                    name={name}
                    trackNumberId={locationTrack?.trackNumberId}
                    layoutContext={layoutContext}
                    updateFreeText={(value) => {
                        updateProp('namingScheme', { ...name, freeText: value });
                    }}
                    onCommitFreeText={() => onCommitField('namingScheme')}
                    updateSpecifier={(value) =>
                        updateProp('namingScheme', {
                            ...name,
                            specifier: value,
                        })
                    }
                    onCommitSpecifier={() => onCommitField('namingScheme')}
                    getNameErrors={() => getVisibleErrorsByProp('namingScheme')}
                />
            );
        case LocationTrackNamingScheme.WITHIN_OPERATING_POINT:
            return (
                <LocationTrackNameFreeText
                    name={name}
                    updateFreeText={(value) => {
                        updateProp('namingScheme', { ...name, freeText: value });
                    }}
                    onCommitFreeText={() => onCommitField('namingScheme')}
                    getNameErrors={() => getVisibleErrorsByProp('namingScheme')}
                />
            );
        case LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS:
            return (
                <LocationTrackNameBetweenOperatingPoints
                    name={name}
                    locationTrack={locationTrack}
                    layoutContext={layoutContext}
                    updateSpecifier={(value) =>
                        updateProp('namingScheme', {
                            ...name,
                            specifier: value,
                        })
                    }
                    onCommitSpecifier={() => onCommitField('namingScheme')}
                    getNameErrors={() => getVisibleErrorsByProp('namingScheme')}
                />
            );
        case LocationTrackNamingScheme.CHORD:
            return (
                <LocationTrackNameChord
                    locationTrack={locationTrack}
                    layoutContext={layoutContext}
                />
            );
        default:
            return <React.Fragment />;
    }
};

type LocationTrackEditDialogNameSectionProps = {
    state: LocationTrackEditState;
    existingLocationTrack: LayoutLocationTrack | undefined;
    layoutContext: LayoutContext;
    updateProp: <TKey extends keyof LocationTrackSaveRequest>(
        key: TKey,
        value: LocationTrackSaveRequest[TKey],
    ) => void;
    onCommitField: (prop: keyof LocationTrackSaveRequest) => void;
    getVisibleErrorsByProp: (prop: keyof LocationTrackSaveRequest) => string[];
    onEditTrack: (id: LocationTrackId) => void;
};

export const LocationTrackEditDialogNameSection: React.FC<
    LocationTrackEditDialogNameSectionProps
> = ({
    state,
    existingLocationTrack,
    layoutContext,
    updateProp,
    onCommitField,
    getVisibleErrorsByProp,
    onEditTrack: _onEditTrack,
}) => {
    const { t } = useTranslation();

    /*const validTrackName = !state.validationIssues.some((e) => e.field === 'namingScheme')
    ? state.locationTrack.namingScheme
    : undefined;
const trackWithSameName = ifDefined(
    useConflictingTracks(
        state.locationTrack.trackNumberId,
        [validTrackName],
        props.locationTrack?.id ? [props.locationTrack.id] : [],
        layoutContextDraft,
    ),
    first,
);*/

    /*const moveToEditLinkText = (track: LayoutLocationTrack) => {
        return track.state === 'DELETED'
            ? t('location-track-dialog.move-to-edit-deleted')
            : t('location-track-dialog.move-to-edit', { name: track.name });
    };*/

    return (
        <React.Fragment>
            <FieldLayout
                label={`${t('location-track-dialog.track-logo')} *`}
                value={
                    <div className={styles['location-track-edit-dialog__description']}>
                        <FieldLayout
                            label={`${t('location-track-dialog.naming-scheme')} *`}
                            value={
                                <Dropdown
                                    qa-id="location-track-naming-scheme"
                                    value={state.locationTrack?.namingScheme.scheme}
                                    options={locationTrackNamingSchemes}
                                    onChange={(e) =>
                                        e &&
                                        updateProp(
                                            'namingScheme',
                                            locationTrackNameByNamingScheme(e),
                                        )
                                    }
                                    onBlur={() => onCommitField('namingScheme')}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp('namingScheme')}
                        />
                        <LocationTrackNameParts
                            name={state.locationTrack?.namingScheme}
                            locationTrack={existingLocationTrack}
                            layoutContext={layoutContext}
                            updateProp={updateProp}
                            onCommitField={onCommitField}
                            getVisibleErrorsByProp={getVisibleErrorsByProp}
                        />
                    </div>
                }
                errors={getVisibleErrorsByProp('namingScheme')}
            />
            {/*trackWithSameName && (
                <>
                    <div className={styles['location-track-edit-dialog__alert-color']}>
                        {trackWithSameName.state === 'DELETED'
                            ? t('location-track-dialog.name-in-use-deleted')
                            : t('location-track-dialog.name-in-use')}
                    </div>
                    <AnchorLink
                        className={styles['location-track-edit-dialog__alert']}
                        onClick={() => onEditTrack(trackWithSameName.id)}>
                        {moveToEditLinkText(trackWithSameName)}
                    </AnchorLink>
                </>
            )*/}
        </React.Fragment>
    );
};
