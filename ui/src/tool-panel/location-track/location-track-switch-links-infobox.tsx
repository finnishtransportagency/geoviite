import React from 'react';
import { useTranslation } from 'react-i18next';
import Infobox from 'tool-panel/infobox/infobox';
import { LayoutLocationTrack, LayoutSwitchId } from 'track-layout/track-layout-model';
import { useLocationTrackInfoboxExtras } from 'track-layout/track-layout-react-utils';
import { LayoutContext } from 'common/common-model';
import styles from './location-track-switch-links-infobox.scss';
import { ChangeTimes } from 'common/common-slice';
import { OnSelectOptions } from 'selection/selection-model';
import { ShowMoreButton } from 'show-more-button/show-more-button';
import { LayoutValidationIssue, ValidatedSwitch } from 'publication/publication-model';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { getSwitches, getSwitchesValidation } from 'track-layout/layout-switch-api';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import { LocationTrackSwitchRow } from './location-track-switch-row';
import { useTrackLayoutAppSelector } from 'store/hooks';

const maxSwitchesToDisplay = 10;

type LocationTrackVerticalGeometryInfoboxProps = {
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
    locationTrack: LayoutLocationTrack;
    layoutContext: LayoutContext;
    changeTimes: ChangeTimes;
    onSelect: (items: OnSelectOptions) => void;
};

function collectValidationBySwitch(validatedSwitches: ValidatedSwitch[]): {
    [p: LayoutSwitchId]: LayoutValidationIssue[];
} {
    const validationBySwitch: { [switchId: LayoutSwitchId]: LayoutValidationIssue[] } = {};
    validatedSwitches.forEach((validatedSwitch) => {
        validationBySwitch[validatedSwitch.id] = validatedSwitch.errors;
    });
    return validationBySwitch;
}

export const LocationTrackSwitchLinksInfobox: React.FC<
    LocationTrackVerticalGeometryInfoboxProps
> = ({
    contentVisible,
    onContentVisibilityChange,
    locationTrack,
    layoutContext,
    changeTimes,
    onSelect,
}) => {
    const { t } = useTranslation();
    const switches =
        useLocationTrackInfoboxExtras(locationTrack.id, layoutContext, changeTimes)[0]?.switches ??
        [];
    const switchIds = switches.map((s) => s.switchId);
    const linkingState = useTrackLayoutAppSelector((s) => s.linkingState);
    const splittingState = useTrackLayoutAppSelector((s) => s.splittingState);

    const loaderDeps = [
        JSON.stringify(switchIds),
        locationTrack.id,
        layoutContext.branch,
        layoutContext.publicationState,
        changeTimes.layoutLocationTrack,
        changeTimes.layoutSwitch,
        changeTimes.layoutTrackNumber,
        changeTimes.layoutKmPost,
    ];

    const [switchItems, switchItemLoadStatus] = useLoaderWithStatus(
        () => getSwitches(switchIds, layoutContext),
        loaderDeps,
    );

    const [validatedSwitches, validationLoaderStatus] = useLoaderWithStatus(
        () => getSwitchesValidation(layoutContext, switchIds),
        loaderDeps,
    );
    const validationBySwitch = collectValidationBySwitch(validatedSwitches ?? []);

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
        <Infobox
            title={t('tool-panel.location-track.switch-links.heading', {
                count: switchesAll.length,
            })}
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
                                    <LocationTrackSwitchRow
                                        key={switchItem.id}
                                        layoutContext={layoutContext}
                                        switchItem={switchItem}
                                        location={location}
                                        displayAddress={address}
                                        validationIssues={validationIssues}
                                        locationTrack={locationTrack}
                                        onSelect={onSelect}
                                        isLinkingOrSplitting={!!linkingState || !!splittingState}
                                    />
                                ))}
                            </div>
                            {switches.length > maxSwitchesToDisplay && (
                                <ShowMoreButton
                                    expanded={showAllSwitches}
                                    onShowMore={() => setShowAllSwitches(!showAllSwitches)}
                                    showMoreText={t(
                                        'tool-panel.location-track.switch-links.show-more',
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
    );
};
