import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { AlignmentExtension, ExtendingAlignment } from 'linking/linking-model';
import { LayoutContext } from 'common/common-model';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import React from 'react';
import { useTranslation } from 'react-i18next';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import {
    TrackNumberBadge,
    TrackNumberBadgeStatus,
} from 'geoviite-design-lib/alignment/track-number-badge';
import { extendReferenceLine } from 'track-layout/layout-track-number-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { stopExtendingAlignment } from 'linking/alignment-extension-utils';

type TrackNumberGeometryExtensionInfoboxContainerProps = {
    trackNumber: LayoutTrackNumber;
    linkingState: ExtendingAlignment;
    layoutContext: LayoutContext;
};

export const TrackNumberGeometryExtensionInfoboxContainer: React.FC<
    TrackNumberGeometryExtensionInfoboxContainerProps
> = ({ trackNumber, linkingState, layoutContext }) => {
    const { t } = useTranslation();
    const delegates = createDelegates(TrackLayoutActions);

    return (
        <TrackNumberGeometryExtensionInfobox
            trackNumber={trackNumber}
            linkingState={linkingState}
            onClearExtension={() => delegates.clearAlignmentExtension()}
            onStopExtendingGeometry={() => stopExtendingAlignment(delegates)}
            onSaveExtension={async (extension) => {
                try {
                    await extendReferenceLine(
                        layoutContext.branch,
                        trackNumber.id,
                        extension.end,
                        extension.location,
                    );
                } catch {
                    Snackbar.error(
                        t('tool-panel.reference-line.geometry-extension.extension-failed'),
                    );
                    return;
                }
                Snackbar.success(
                    t('tool-panel.reference-line.geometry-extension.extension-saved', {
                        trackNumber: trackNumber.number,
                    }),
                );
                stopExtendingAlignment(delegates);
            }}
        />
    );
};

type TrackNumberGeometryExtensionInfoboxProps = {
    trackNumber: LayoutTrackNumber;
    linkingState: ExtendingAlignment;
    onClearExtension: () => void;
    onStopExtendingGeometry: () => void;
    onSaveExtension: (extension: AlignmentExtension) => Promise<void>;
};

const TrackNumberGeometryExtensionInfobox: React.FC<TrackNumberGeometryExtensionInfoboxProps> = ({
    trackNumber,
    linkingState,
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
            title={t('tool-panel.reference-line.geometry-extension.title')}
            contentVisible={true}>
            <InfoboxContent>
                <InfoboxField label={t('tool-panel.reference-line.geometry-extension.track-number')}>
                    <TrackNumberBadge
                        trackNumber={trackNumber}
                        status={TrackNumberBadgeStatus.SELECTED}
                    />
                </InfoboxField>
                <InfoboxField
                    label={t('tool-panel.reference-line.geometry-extension.extension-end')}>
                    {extension === undefined
                        ? t('tool-panel.reference-line.geometry-extension.draw-hint')
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
                        {t('tool-panel.reference-line.geometry-extension.clear')}
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
