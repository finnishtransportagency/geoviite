import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import styles from './location-track-edit-dialog.scss';
import { LocationTrackEditState } from 'tool-panel/location-track/dialog/location-track-edit-store';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { LocationTrackSaveRequest } from 'linking/linking-model';
import {
    locationTrackNameSpecifiers,
    locationTrackNamingSchemes,
} from 'utils/enum-localization-utils';
import {
    LayoutLocationTrack,
    LocationTrackId,
    LocationTrackNamingScheme,
    LocationTrackSpecifier,
} from 'track-layout/track-layout-model';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';

type LocationTrackNameFreeTextProps = {
    request: LocationTrackSaveRequest;
    updateFreeText: (freeText: string) => void;
    onCommitFreeText: () => void;
    getFreeTextErrors: () => string[];
};

type LocationTrackNameTrackNumberProps = {
    request: LocationTrackSaveRequest;
    updateFreeText: (freeText: string) => void;
    updateSpecifier: (specifier: LocationTrackSpecifier | undefined) => void;
    onCommitFreeText: () => void;
    onCommitSpecifier: () => void;
    getFreeTextErrors: () => string[];
    getSpecifierErrors: () => string[];
};

type LocationTrackNameBetweenOperatingPointsProps = {
    request: LocationTrackSaveRequest;
    updateSpecifier: (specifier: LocationTrackSpecifier | undefined) => void;
    onCommitSpecifier: () => void;
    getSpecifierErrors: () => string[];
};

const LocationTrackNameFreeText: React.FC<LocationTrackNameFreeTextProps> = ({
    request,
    updateFreeText,
    onCommitFreeText,
    getFreeTextErrors,
}) => {
    const { t } = useTranslation();
    return (
        <FieldLayout
            label={`${t('location-track-dialog.name-free-text')} *`}
            value={
                <TextField
                    qa-id="location-track-name-free-text"
                    value={request.nameFreeText}
                    onChange={(e) => updateFreeText && updateFreeText(e.target.value)}
                    onBlur={() => onCommitFreeText && onCommitFreeText()}
                    wide
                />
            }
            errors={getFreeTextErrors()}
        />
    );
};

const LocationTrackNameTrackNumber: React.FC<LocationTrackNameTrackNumberProps> = ({
    request,
    updateFreeText,
    onCommitFreeText,
    updateSpecifier,
    onCommitSpecifier,
    getFreeTextErrors,
    getSpecifierErrors,
}) => {
    const { t } = useTranslation();
    return (
        <React.Fragment>
            <FieldLayout
                label={`${t('location-track-dialog.name-specifier')} *`}
                value={
                    <Dropdown
                        qa-id="location-track-name-specifier"
                        value={request.nameSpecifier}
                        options={locationTrackNameSpecifiers}
                        canUnselect={true}
                        onChange={(e) => updateSpecifier && updateSpecifier(e)}
                        onBlur={() => onCommitSpecifier && onCommitSpecifier()}
                        wide
                    />
                }
                errors={getSpecifierErrors()}
            />
            <FieldLayout
                label={`${t('location-track-dialog.operating-point-range')} *`}
                value={
                    <TextField
                        qa-id="location-track-name-operating-point-range"
                        value={request.nameFreeText}
                        onChange={(e) => updateFreeText && updateFreeText(e.target.value)}
                        onBlur={() => onCommitFreeText && onCommitFreeText()}
                        wide
                    />
                }
                errors={getFreeTextErrors()}
            />
        </React.Fragment>
    );
};

const LocationTrackNameBetweenOperatingPoints: React.FC<
    LocationTrackNameBetweenOperatingPointsProps
> = ({ request, updateSpecifier, onCommitSpecifier, getSpecifierErrors }) => {
    const { t } = useTranslation();
    return (
        <FieldLayout
            label={`${t('location-track-dialog.name-specifier')} *`}
            value={
                <Dropdown
                    qa-id="location-track-name-specifier"
                    value={request.nameSpecifier}
                    options={locationTrackNameSpecifiers}
                    canUnselect={true}
                    onChange={(e) => updateSpecifier && updateSpecifier(e)}
                    onBlur={() => onCommitSpecifier && onCommitSpecifier()}
                    wide
                />
            }
            errors={getSpecifierErrors()}
        />
    );
};

export const LocationTrackNameParts: React.FC<{
    state: LocationTrackEditState;
    updateProp: <TKey extends keyof LocationTrackSaveRequest>(
        key: TKey,
        value: LocationTrackSaveRequest[TKey],
    ) => void;
    onCommitField: (prop: keyof LocationTrackSaveRequest) => void;
    getVisibleErrorsByProp: (prop: keyof LocationTrackSaveRequest) => string[];
}> = ({ state, updateProp, onCommitField, getVisibleErrorsByProp }) => {
    switch (state.locationTrack.namingScheme) {
        case LocationTrackNamingScheme.FREE_TEXT:
            return (
                <React.Fragment>
                    <LocationTrackNameFreeText
                        request={state.locationTrack}
                        updateFreeText={(value) => updateProp('nameFreeText', value)}
                        onCommitFreeText={() => onCommitField('nameFreeText')}
                        getFreeTextErrors={() => getVisibleErrorsByProp('nameFreeText')}
                    />
                </React.Fragment>
            );
        case LocationTrackNamingScheme.TRACK_NUMBER_TRACK:
            return (
                <LocationTrackNameTrackNumber
                    request={state.locationTrack}
                    updateFreeText={(value) => updateProp('nameFreeText', value)}
                    updateSpecifier={(value) => updateProp('nameSpecifier', value)}
                    onCommitFreeText={() => onCommitField('nameFreeText')}
                    onCommitSpecifier={() => onCommitField('nameSpecifier')}
                    getFreeTextErrors={() => getVisibleErrorsByProp('nameFreeText')}
                    getSpecifierErrors={() => getVisibleErrorsByProp('nameSpecifier')}
                />
            );
        case LocationTrackNamingScheme.WITHIN_OPERATING_POINT:
            return (
                <LocationTrackNameFreeText
                    request={state.locationTrack}
                    updateFreeText={(value) => updateProp('nameFreeText', value)}
                    onCommitFreeText={() => onCommitField('nameFreeText')}
                    getFreeTextErrors={() => getVisibleErrorsByProp('nameFreeText')}
                />
            );
        case LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS:
            return (
                <LocationTrackNameBetweenOperatingPoints
                    request={state.locationTrack}
                    updateSpecifier={(value) => updateProp('nameSpecifier', value)}
                    onCommitSpecifier={() => onCommitField('nameSpecifier')}
                    getSpecifierErrors={() => getVisibleErrorsByProp('nameSpecifier')}
                />
            );
        case LocationTrackNamingScheme.CHORD:
            return <React.Fragment />;
        default:
            return <React.Fragment />;
    }
};

type LocationTrackEditDialogNameSectionProps = {
    state: LocationTrackEditState;
    updateProp: <TKey extends keyof LocationTrackSaveRequest>(
        key: TKey,
        value: LocationTrackSaveRequest[TKey],
    ) => void;
    onCommitField: (prop: keyof LocationTrackSaveRequest) => void;
    getVisibleErrorsByProp: (prop: keyof LocationTrackSaveRequest) => string[];
    onEditTrack: (id: LocationTrackId) => void;
    fullName: string;
    trackWithSameName: LayoutLocationTrack | undefined;
};

export const LocationTrackEditDialogNameSection: React.FC<
    LocationTrackEditDialogNameSectionProps
> = ({
    state,
    updateProp,
    onCommitField,
    getVisibleErrorsByProp,
    onEditTrack,
    fullName,
    trackWithSameName,
}) => {
    const { t } = useTranslation();
    const moveToEditLinkText = (track: LayoutLocationTrack) => {
        return track.state === 'DELETED'
            ? t('location-track-dialog.move-to-edit-deleted')
            : t('location-track-dialog.move-to-edit', { name: track.name });
    };

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
                                    qaId="location-track-naming-scheme"
                                    value={state.locationTrack?.namingScheme}
                                    options={locationTrackNamingSchemes}
                                    onChange={(e) => e && updateProp('namingScheme', e)}
                                    onBlur={() => onCommitField('namingScheme')}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp('namingScheme')}
                        />
                        <LocationTrackNameParts
                            state={state}
                            updateProp={updateProp}
                            onCommitField={onCommitField}
                            getVisibleErrorsByProp={getVisibleErrorsByProp}
                        />
                        <FieldLayout
                            label={`${t('location-track-dialog.full-name')}`}
                            value={fullName}
                        />
                        {trackWithSameName && (
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
                        )}
                    </div>
                }
            />
        </React.Fragment>
    );
};
