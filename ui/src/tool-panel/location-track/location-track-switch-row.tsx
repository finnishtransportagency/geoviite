import React from 'react';
import { useTranslation } from 'react-i18next';
import { LayoutLocationTrack, LayoutSwitch } from 'track-layout/track-layout-model';
import { LayoutContext, TrackMeter } from 'common/common-model';
import { Point } from 'model/geometry';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import styles from './location-track-switch-links-infobox.scss';
import NavigableTrackMeter from 'geoviite-design-lib/track-meter/navigable-track-meter';
import { SwitchBadge } from 'geoviite-design-lib/switch/switch-badge';
import { OnSelectOptions } from 'selection/selection-model';
import { LayoutValidationIssue } from 'publication/publication-model';
import infoboxStyles from 'tool-panel/infobox/infobox.module.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { LocationTrackDetachSwitchDialog } from './dialog/location-track-detach-switch-dialog';

type LocationTrackSwitchRowProps = {
    layoutContext: LayoutContext;
    switchItem: LayoutSwitch;
    validationIssues: LayoutValidationIssue[];
    location?: Point;
    displayAddress?: TrackMeter;
    locationTrack: LayoutLocationTrack;
    onSelect: (items: OnSelectOptions) => void;
};

export const LocationTrackSwitchRow: React.FC<LocationTrackSwitchRowProps> = ({
    layoutContext,
    switchItem,
    validationIssues,
    location,
    displayAddress,
    locationTrack,
    onSelect,
}) => {
    const { t } = useTranslation();
    const [showDetachDialog, setShowDetachDialog] = React.useState(false);
    const remarkClassNames = createClassName(
        styles['location-track-switch-links-infobox-list__remark'],
        infoboxStyles['infobox__list-cell--strong'],
    );

    return (
        <>
            <div
                title={validationIssues
                    .map((issue) => t(issue.localizationKey, issue.params))
                    .join('\n')}>
                <SwitchBadge
                    switchItem={switchItem}
                    switchIsValid={validationIssues.length === 0}
                    onClick={() =>
                        onSelect({
                            switches: [switchItem.id],
                            selectedTab: { id: switchItem.id, type: 'SWITCH' },
                        })
                    }
                />
            </div>
            <div className={infoboxStyles['infobox__list-cell--strong']}>
                {displayAddress === undefined ? (
                    t('tool-panel.location-track.switch-links.no-location')
                ) : (
                    <NavigableTrackMeter
                        trackMeter={displayAddress}
                        displayDecimals={false}
                        location={location}
                    />
                )}
            </div>
            <div className={remarkClassNames}>
                {switchItem.stateCategory === 'NOT_EXISTING' &&
                    t('tool-panel.location-track.switch-links.not-existing')}
            </div>
            <div>
                {layoutContext.publicationState === 'DRAFT' && (
                    <Button
                        size={ButtonSize.SMALL}
                        variant={ButtonVariant.GHOST}
                        onClick={() => setShowDetachDialog(true)}>
                        {t('tool-panel.location-track.switch-links.detach')}
                    </Button>
                )}
            </div>
            {showDetachDialog && (
                <LocationTrackDetachSwitchDialog
                    layoutContext={layoutContext}
                    locationTrackId={locationTrack.id}
                    locationTrackName={locationTrack.name}
                    switchId={switchItem.id}
                    switchName={switchItem.name}
                    onClose={() => setShowDetachDialog(false)}
                    onDetached={() => setShowDetachDialog(false)}
                />
            )}
        </>
    );
};
