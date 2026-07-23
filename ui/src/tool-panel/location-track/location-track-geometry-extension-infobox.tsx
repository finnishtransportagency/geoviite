import { LayoutLocationTrack } from 'track-layout/track-layout-model';
import { AlignmentExtension, ExtendingAlignment } from 'linking/linking-model';
import { LayoutContext } from 'common/common-model';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import React from 'react';
import { useTranslation } from 'react-i18next';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Radio } from 'vayla-design-lib/radio/radio';
import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import {
    LocationTrackBadge,
    LocationTrackBadgeStatus,
} from 'geoviite-design-lib/alignment/location-track-badge';
import { extendLocationTrack } from 'track-layout/layout-location-track-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { stopExtendingAlignment } from 'linking/alignment-extension-utils';

type LocationTrackGeometryExtensionInfoboxContainerProps = {
    locationTrack: LayoutLocationTrack;
    linkingState: ExtendingAlignment;
    layoutContext: LayoutContext;
};

export const LocationTrackGeometryExtensionInfoboxContainer: React.FC<
    LocationTrackGeometryExtensionInfoboxContainerProps
> = ({ locationTrack, linkingState, layoutContext }) => {
    const { t } = useTranslation();
    const delegates = createDelegates(TrackLayoutActions);

    return (
        <LocationTrackGeometryExtensionInfobox
            locationTrack={locationTrack}
            linkingState={linkingState}
            onSetDirectionSnap={(directionSnap) =>
                delegates.setAlignmentDirectionSnap(directionSnap)
            }
            onClearExtension={() => delegates.clearAlignmentExtension()}
            onStopExtendingGeometry={() => stopExtendingAlignment(delegates)}
            onSaveExtension={async (extension) => {
                try {
                    await extendLocationTrack(
                        layoutContext.branch,
                        locationTrack.id,
                        extension.end,
                        extension.location,
                    );
                } catch {
                    Snackbar.error(
                        t('tool-panel.location-track.geometry-extension.extension-failed'),
                    );
                    return;
                }
                Snackbar.success(
                    t('tool-panel.location-track.geometry-extension.extension-saved', {
                        track: locationTrack.name,
                    }),
                );
                stopExtendingAlignment(delegates);
            }}
        />
    );
};

type LocationTrackGeometryExtensionInfoboxProps = {
    locationTrack: LayoutLocationTrack;
    linkingState: ExtendingAlignment;
    onSetDirectionSnap: (directionSnap: boolean) => void;
    onClearExtension: () => void;
    onStopExtendingGeometry: () => void;
    onSaveExtension: (extension: AlignmentExtension) => Promise<void>;
};

const LocationTrackGeometryExtensionInfobox: React.FC<
    LocationTrackGeometryExtensionInfoboxProps
> = ({
    locationTrack,
    linkingState,
    onSetDirectionSnap,
    onClearExtension,
    onStopExtendingGeometry,
    onSaveExtension,
}) => {
    const { t } = useTranslation();

    const [saving, setSaving] = React.useState(false);

    const extension = linkingState.extension;

    const saveExtension = () => {
        if (extension === undefined) {
            return;
        }
        setSaving(true);
        onSaveExtension(extension).finally(() => setSaving(false));
    };

    return (
        <Infobox
            title={t('tool-panel.location-track.geometry-extension.title')}
            contentVisible={true}>
            <InfoboxContent>
                <InfoboxField label={t('tool-panel.location-track.geometry-extension.track')}>
                    <LocationTrackBadge
                        locationTrack={locationTrack}
                        status={LocationTrackBadgeStatus.SELECTED}
                    />
                </InfoboxField>
                <InfoboxField
                    label={t('tool-panel.location-track.geometry-extension.direction-snap')}>
                    <div>
                        <div>
                            <Radio
                                qaId={'alignment-extension-no-direction-snap'}
                                checked={!linkingState.directionSnap}
                                onChange={() => onSetDirectionSnap(false)}>
                                {t(
                                    'tool-panel.location-track.geometry-extension.no-direction-snap',
                                )}
                            </Radio>
                        </div>
                        <div>
                            <Radio
                                qaId={'alignment-extension-snap-to-end-direction'}
                                checked={linkingState.directionSnap}
                                onChange={() => onSetDirectionSnap(true)}>
                                {t(
                                    'tool-panel.location-track.geometry-extension.snap-to-end-direction',
                                )}
                            </Radio>
                        </div>
                    </div>
                </InfoboxField>
                <InfoboxField
                    label={t('tool-panel.location-track.geometry-extension.extension-end')}>
                    {extension === undefined
                        ? t('tool-panel.location-track.geometry-extension.draw-hint')
                        : t(
                              extension.end === 'START'
                                  ? 'tool-panel.location-track.start-point'
                                  : 'tool-panel.location-track.end-point',
                          )}
                </InfoboxField>
                <InfoboxButtons>
                    <Button
                        size={ButtonSize.SMALL}
                        variant={ButtonVariant.SECONDARY}
                        disabled={saving}
                        onClick={onStopExtendingGeometry}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        size={ButtonSize.SMALL}
                        variant={ButtonVariant.SECONDARY}
                        disabled={saving || extension === undefined}
                        onClick={onClearExtension}>
                        {t('tool-panel.location-track.geometry-extension.clear')}
                    </Button>
                    <Button
                        size={ButtonSize.SMALL}
                        disabled={saving || extension === undefined}
                        isProcessing={saving}
                        onClick={saveExtension}>
                        {t('button.save')}
                    </Button>
                </InfoboxButtons>
            </InfoboxContent>
        </Infobox>
    );
};
