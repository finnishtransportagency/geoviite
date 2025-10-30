import React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { formatToTM35FINString } from 'utils/geography-utils';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { LinkingType } from 'linking/linking-model';
import Infobox from 'tool-panel/infobox/infobox';
import { OperationalPoint } from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import {
    OperationalPointInfoboxVisibilities,
    TrackLayoutState,
} from 'track-layout/track-layout-slice';
import {
    updateInternalOperationalPointLocation,
    updateOperationalPointArea,
} from 'track-layout/layout-operational-point-api';
import * as SnackBar from 'geoviite-design-lib/snackbar/snackbar';
import infoboxStyles from 'tool-panel/infobox/infobox.module.scss';

type OperationalPointLocationInfoboxProps = {
    operationalPoint: OperationalPoint;
    layoutContext: LayoutContext;
    layoutState: TrackLayoutState;
    visibilities: OperationalPointInfoboxVisibilities;
    visibilityChange: (key: keyof OperationalPointInfoboxVisibilities) => void;
    onShowOnMap: () => void;
    onStartPlacingLocation: () => void;
    onStopPlacingLocation: () => void;
    onStartPlacingArea: () => void;
    onStopPlacingArea: () => void;
};

export const OperationalPointLocationInfobox: React.FC<OperationalPointLocationInfoboxProps> = ({
    operationalPoint,
    layoutContext,
    layoutState,
    visibilities,
    visibilityChange,
    onShowOnMap,
    onStartPlacingLocation,
    onStopPlacingLocation,
    onStartPlacingArea,
    onStopPlacingArea,
}) => {
    const { t } = useTranslation();

    const [locationUpdateInProgress, setLocationUpdateInProgress] = React.useState(false);
    const isExternal = operationalPoint.origin === 'RATKO';

    const saveLocation = () => {
        if (
            layoutState.linkingState?.type === LinkingType.PlacingOperationalPoint &&
            !!layoutState.linkingState.location
        ) {
            try {
                setLocationUpdateInProgress(true);
                updateInternalOperationalPointLocation(
                    layoutState.linkingState.operationalPoint.id,
                    layoutState.linkingState.location,
                    layoutContext,
                );
                SnackBar.success('tool-panel.operational-point.location-update-succeeded');
                onStopPlacingLocation();
            } finally {
                setLocationUpdateInProgress(false);
            }
        }
    };

    const savePolygon = () => {
        if (
            layoutState.linkingState?.type === LinkingType.PlacingOperationalPointArea &&
            !!layoutState.linkingState.polygon
        ) {
            try {
                setLocationUpdateInProgress(true);
                updateOperationalPointArea(
                    layoutState.linkingState.operationalPoint.id,
                    layoutState.linkingState.polygon,
                    layoutContext,
                );
                SnackBar.success('tool-panel.operational-point.location-update-succeeded');
                onStopPlacingArea();
            } finally {
                setLocationUpdateInProgress(false);
            }
        }
    };

    return (
        <Infobox
            contentVisible={visibilities.location}
            onContentVisibilityChange={() => visibilityChange('location')}
            title={t('tool-panel.operational-point.location-heading')}
            qa-id="operational-point-infobox-location">
            <InfoboxContent>
                <InfoboxField
                    label={t('tool-panel.operational-point.location')}
                    value={
                        operationalPoint.location
                            ? formatToTM35FINString(operationalPoint.location)
                            : '-'
                    }
                />
                <InfoboxButtons>
                    <Button
                        variant={ButtonVariant.SECONDARY}
                        size={ButtonSize.SMALL}
                        disabled={!operationalPoint.location}
                        onClick={onShowOnMap}>
                        {t('tool-panel.operational-point.focus-on-map')}
                    </Button>
                </InfoboxButtons>
                {(!layoutState.linkingState ||
                    layoutState.linkingState.type !== LinkingType.PlacingOperationalPoint) && (
                    <InfoboxButtons>
                        <Button
                            variant={ButtonVariant.SECONDARY}
                            size={ButtonSize.SMALL}
                            disabled={
                                layoutContext.publicationState === 'OFFICIAL' ||
                                !!layoutState.linkingState ||
                                isExternal
                            }
                            onClick={onStartPlacingLocation}
                            title={
                                isExternal
                                    ? t(
                                          'tool-panel.operational-point.cannot-set-location-for-external',
                                      )
                                    : undefined
                            }>
                            {t('tool-panel.operational-point.set-location')}
                        </Button>
                    </InfoboxButtons>
                )}
                {layoutState.linkingState &&
                    layoutState.linkingState.type === LinkingType.PlacingOperationalPoint && (
                        <React.Fragment>
                            <p className={infoboxStyles['infobox__guide-text']}>
                                {t('tool-panel.operational-point.set-location-help')}
                            </p>
                            <InfoboxButtons>
                                <Button
                                    variant={ButtonVariant.SECONDARY}
                                    size={ButtonSize.SMALL}
                                    disabled={locationUpdateInProgress}
                                    onClick={onStopPlacingLocation}>
                                    {t('button.cancel')}
                                </Button>
                                <Button
                                    variant={ButtonVariant.PRIMARY}
                                    size={ButtonSize.SMALL}
                                    disabled={
                                        !layoutState.linkingState.location ||
                                        locationUpdateInProgress
                                    }
                                    isProcessing={locationUpdateInProgress}
                                    onClick={saveLocation}>
                                    {t('button.save')}
                                </Button>
                            </InfoboxButtons>
                        </React.Fragment>
                    )}
                {(!layoutState.linkingState ||
                    layoutState.linkingState.type !== LinkingType.PlacingOperationalPointArea) && (
                    <InfoboxButtons>
                        <Button
                            variant={ButtonVariant.SECONDARY}
                            size={ButtonSize.SMALL}
                            disabled={
                                layoutContext.publicationState === 'OFFICIAL' ||
                                !!layoutState.linkingState
                            }
                            onClick={onStartPlacingArea}>
                            {t('tool-panel.operational-point.set-area')}
                        </Button>
                    </InfoboxButtons>
                )}
                {layoutState.linkingState &&
                    layoutState.linkingState.type === LinkingType.PlacingOperationalPointArea && (
                        <React.Fragment>
                            <p className={infoboxStyles['infobox__guide-text']}>
                                {operationalPoint.polygon
                                    ? t('tool-panel.operational-point.update-area-help')
                                    : t('tool-panel.operational-point.insert-area-help')}
                            </p>
                            <InfoboxButtons>
                                <Button
                                    variant={ButtonVariant.SECONDARY}
                                    size={ButtonSize.SMALL}
                                    disabled={locationUpdateInProgress}
                                    onClick={onStopPlacingArea}>
                                    {t('button.cancel')}
                                </Button>
                                <Button
                                    variant={ButtonVariant.PRIMARY}
                                    size={ButtonSize.SMALL}
                                    disabled={
                                        !layoutState.linkingState.polygon ||
                                        locationUpdateInProgress
                                    }
                                    isProcessing={locationUpdateInProgress}
                                    onClick={savePolygon}>
                                    {t('button.save')}
                                </Button>
                            </InfoboxButtons>
                        </React.Fragment>
                    )}
            </InfoboxContent>
        </Infobox>
    );
};
