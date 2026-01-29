import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import React from 'react';
import { useTranslation } from 'react-i18next';
import Infobox from 'tool-panel/infobox/infobox';
import { LayoutLocationTrack, LayoutSwitch, LayoutSwitchId } from 'track-layout/track-layout-model';
import { useLocationTrackInfoboxExtras } from 'track-layout/track-layout-react-utils';
import { LayoutContext, TrackMeter } from 'common/common-model';
import { Point } from 'model/geometry';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { detachSwitchFromLocationTrack } from 'track-layout/layout-location-track-api';
import { updateLocationTrackChangeTime } from 'common/change-time-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import styles from './location-track-switch-links-infobox.scss';
import { ChangeTimes } from 'common/common-slice';
import NavigableTrackMeter from 'geoviite-design-lib/track-meter/navigable-track-meter';
import { SwitchBadge } from 'geoviite-design-lib/switch/switch-badge';
import { OnSelectOptions } from 'selection/selection-model';
import { ShowMoreButton } from 'show-more-button/show-more-button';
import { LayoutValidationIssue, ValidatedLocationTrack } from 'publication/publication-model';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { getSwitches } from 'track-layout/layout-switch-api';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import infoboxStyles from 'tool-panel/infobox/infobox.module.scss';
import { createClassName } from 'vayla-design-lib/utils';

const maxSwitchesToDisplay = 10;

type LocationTrackVerticalGeometryInfoboxProps = {
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
    locationTrack: LayoutLocationTrack;
    validation: ValidatedLocationTrack | undefined;
    validationLoaderStatus: LoaderStatus;
    layoutContext: LayoutContext;
    changeTimes: ChangeTimes;
    onSelect: (items: OnSelectOptions) => void;
};

function collectValidationBySwitch(validation: ValidatedLocationTrack | undefined): {
    [p: LayoutSwitchId]: LayoutValidationIssue[];
} {
    const validationBySwitch: { [switchId: LayoutSwitchId]: LayoutValidationIssue[] } = {};
    (validation?.errors ?? []).forEach((issue) => {
        (issue.inRelationTo ?? []).forEach((inRelationTo) => {
            if (inRelationTo.type === 'SWITCH') {
                const switchId = inRelationTo.id as LayoutSwitchId;
                validationBySwitch[switchId] ??= [];
                validationBySwitch[switchId].push(issue);
            }
        });
    });
    return validationBySwitch;
}

export const LocationTrackSwitchLinksInfobox: React.FC<
    LocationTrackVerticalGeometryInfoboxProps
> = ({
    contentVisible,
    onContentVisibilityChange,
    locationTrack,
    validation,
    validationLoaderStatus,
    layoutContext,
    changeTimes,
    onSelect,
}) => {
    const { t } = useTranslation();
    const switches =
        useLocationTrackInfoboxExtras(locationTrack.id, layoutContext, changeTimes)[0]?.switches ??
        [];
    const [showingDialogToDetachSwitch, setShowingDialogToDetachSwitch] = React.useState<
        { id: LayoutSwitchId; name: string } | undefined
    >(undefined);
    const [isDetachingSwitch, setIsDetachingSwitch] = React.useState(false);

    const detachSwitch = () => {
        if (showingDialogToDetachSwitch === undefined) return;
        setIsDetachingSwitch(true);
        detachSwitchFromLocationTrack(
            layoutContext.branch,
            locationTrack.id,
            showingDialogToDetachSwitch.id,
        )
            .then(() => updateLocationTrackChangeTime())
            .then(() => {
                setIsDetachingSwitch(false);
                setShowingDialogToDetachSwitch(undefined);
                Snackbar.success(
                    'tool-panel.location-track.detach-switch-links-dialog.success-toast',
                );
            });
    };
    const switchIds = switches.map((s) => s.switchId);

    const [switchItems, switchItemLoadStatus] = useLoaderWithStatus(
        () =>
            getSwitches(
                switches.map((s) => s.switchId),
                layoutContext,
            ),
        [
            JSON.stringify(switchIds),
            locationTrack.id,
            layoutContext.branch,
            layoutContext.publicationState,
            changeTimes.layoutLocationTrack,
            changeTimes.layoutSwitch,
            changeTimes.layoutTrackNumber,
            changeTimes.layoutKmPost,
        ],
    );
    const validationBySwitch = collectValidationBySwitch(validation);

    const switchesAll = (switchItems ?? []).map((switchItem) => {
        // have to find() rather than zip the arrays together by index, as switchItems and switches can be out of sync
        const si = switches.find((s) => s.switchId === switchItem.id);
        return {
            switchItem,
            location: si?.location,
            address: si?.displayAddress,
            validationIssues: validationBySwitch[switchItem.id] ?? [],
        };
    });

    const [showAllSwitches, setShowAllSwitches] = React.useState(false);

    return (
        <>
            <Infobox
                title={t('tool-panel.location-track.switch-links.heading')}
                qa-id={'location-track-switch-links-infobox'}
                contentVisible={contentVisible}
                onContentVisibilityChange={onContentVisibilityChange}>
                <ProgressIndicatorWrapper
                    indicator={ProgressIndicatorType.Area}
                    inProgress={
                        switchItemLoadStatus !== LoaderStatus.Ready ||
                        validationLoaderStatus !== LoaderStatus.Ready
                    }
                    inline={true}>
                    <InfoboxContent>
                        {switchesAll.length > 0 ? (
                            <React.Fragment>
                                <div className={styles['location-track-switch-links-infobox-list']}>
                                    {(showAllSwitches
                                        ? switchesAll
                                        : switchesAll.slice(0, maxSwitchesToDisplay)
                                    ).map(({ switchItem, location, address, validationIssues }) => (
                                        <LocationTrackSwitchLink
                                            key={switchItem.id}
                                            layoutContext={layoutContext}
                                            switchItem={switchItem}
                                            location={location}
                                            displayAddress={address}
                                            validationIssues={validationIssues}
                                            setShowingDialogToDetachSwitch={
                                                setShowingDialogToDetachSwitch
                                            }
                                            onSelect={onSelect}
                                        />
                                    ))}
                                </div>
                                {switches.length > maxSwitchesToDisplay && (
                                    <ShowMoreButton
                                        expanded={showAllSwitches}
                                        onShowMore={() => setShowAllSwitches(!showAllSwitches)}
                                        showMoreText={t(
                                            'tool-panel.location-track.switch-links.show-more',
                                            {
                                                count: switchesAll.length,
                                            },
                                        )}
                                    />
                                )}
                            </React.Fragment>
                        ) : (
                            <p className={'infobox__text'}>
                                {t('tool-panel.location-track.switch-links.no-switches')}
                            </p>
                        )}
                    </InfoboxContent>
                </ProgressIndicatorWrapper>
            </Infobox>
            {showingDialogToDetachSwitch && (
                <Dialog
                    title={t('tool-panel.location-track.detach-switch-links-dialog.title')}
                    variant={DialogVariant.DARK}
                    allowClose={true}
                    onClose={() => setShowingDialogToDetachSwitch(undefined)}
                    footerContent={
                        <>
                            <Button
                                onClick={() => setShowingDialogToDetachSwitch(undefined)}
                                variant={ButtonVariant.SECONDARY}
                                disabled={isDetachingSwitch}>
                                {t('button.cancel')}
                            </Button>
                            <div className={dialogStyles['dialog__footer-content--right-aligned']}>
                                <Button
                                    disabled={isDetachingSwitch}
                                    isProcessing={isDetachingSwitch}
                                    variant={ButtonVariant.PRIMARY_WARNING}
                                    onClick={() => detachSwitch()}>
                                    {t(
                                        'tool-panel.location-track.detach-switch-links-dialog.detach-button',
                                    )}
                                </Button>
                            </div>
                        </>
                    }>
                    <div className={'dialog__text'}>
                        {t('tool-panel.location-track.detach-switch-links-dialog.message', {
                            switchName: showingDialogToDetachSwitch.name,
                            trackName: locationTrack.name,
                        })}
                    </div>
                </Dialog>
            )}
        </>
    );
};

type ShowingDialogToDetachSwitch = { id: LayoutSwitchId; name: string };

type LocationTrackSwitchLinkProps = {
    layoutContext: LayoutContext;
    switchItem: LayoutSwitch;
    validationIssues: LayoutValidationIssue[];
    location?: Point;
    displayAddress?: TrackMeter;
    setShowingDialogToDetachSwitch: (detach: ShowingDialogToDetachSwitch) => void;
    onSelect: (items: OnSelectOptions) => void;
};
const LocationTrackSwitchLink: React.FC<LocationTrackSwitchLinkProps> = ({
    layoutContext,
    switchItem,
    validationIssues,
    location,
    displayAddress,
    setShowingDialogToDetachSwitch,
    onSelect,
}) => {
    const { t } = useTranslation();
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
                        onClick={() => setShowingDialogToDetachSwitch(switchItem)}>
                        {t('tool-panel.location-track.switch-links.detach')}
                    </Button>
                )}
            </div>
        </>
    );
};
