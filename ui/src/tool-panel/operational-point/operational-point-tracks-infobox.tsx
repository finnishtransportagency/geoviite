import styles from './operational-point-infobox.scss';
import React, { useState } from 'react';
import {
    LocationTrackId,
    OperationalPoint,
    OperationalPointId,
} from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import Infobox from 'tool-panel/infobox/infobox';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { ChangeTimes } from 'common/common-slice';
import { linkingSummaryTranslationInfo } from 'tool-panel/operational-point/operational-point-utils';
import {
    findOperationalPointLocationTracks,
    getLocationTracks,
    linkLocationTracksToOperationalPoint,
    unlinkLocationTracksFromOperationalPoint,
} from 'track-layout/layout-location-track-api';
import { deduplicate, filterNotEmpty, filterUnique } from 'utils/array-utils';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { MessageBox, MessageBoxType } from 'geoviite-design-lib/message-box/message-box';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { updateAllChangeTimes } from 'common/change-time-api';
import { LinkingType } from 'linking/linking-model';
import { OperationalPointTracksDirectionInfobox } from 'tool-panel/operational-point/operational-point-tracks-direction-infobox';

const getTracksForOperationalPoint = async (
    context: LayoutContext,
    operationalPointId: OperationalPointId,
) => {
    const itemAssociation = await findOperationalPointLocationTracks(context, operationalPointId);
    const trackIds = [
        ...(itemAssociation?.assigned ?? []),
        ...(itemAssociation?.overlappingArea ?? []),
    ].filter(filterUnique);
    const items = await getLocationTracks(trackIds, context);
    return { itemAssociation, items };
};

export const doLinkingOperationAndFetchNames = async (
    layoutContext: LayoutContext,
    linkingOperationPromise: Promise<LocationTrackId[]>,
) =>
    await linkingOperationPromise
        ?.then((linkedIds) => getLocationTracks(linkedIds, layoutContext))
        ?.then((tracks) => tracks.map((sw) => sw.name));

type OperationalPointTracksInfoboxProps = {
    contentVisible: boolean;
    onVisibilityChange: (key: 'tracks') => void;
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    changeTimes: ChangeTimes;
    onSelectLocationTrack: (locationTrackId: LocationTrackId) => void;
};

export const OperationalPointTracksInfobox: React.FC<OperationalPointTracksInfoboxProps> = ({
    contentVisible,
    onVisibilityChange,
    layoutContext,
    operationalPoint,
    changeTimes,
    onSelectLocationTrack,
}) => {
    const { t } = useTranslation();
    const delegates = React.useMemo(() => createDelegates(trackLayoutActionCreators), []);
    const linkingState = useTrackLayoutAppSelector((state) => state.linkingState);
    const operationalPointTrackLinkingState =
        linkingState?.type === LinkingType.LinkingOperationalPointTracks ? linkingState : undefined;
    const isEditing = !!operationalPointTrackLinkingState;

    const [trackLinkingInfo, trackLinkingInfoFetchStatus] = useLoaderWithStatus(
        () => getTracksForOperationalPoint(layoutContext, operationalPoint.id),
        [
            operationalPoint.id,
            layoutContext.branch,
            layoutContext.publicationState,
            changeTimes.layoutLocationTrack,
            changeTimes.operationalPoints,
        ],
    );

    const tracksInOperationalPointPolygon = new Set(
        trackLinkingInfo?.itemAssociation?.overlappingArea ?? [],
    );
    const originallyLinkedTracks = trackLinkingInfo?.itemAssociation?.assigned ?? [];

    const linkedItems = operationalPointTrackLinkingState
        ? operationalPointTrackLinkingState.linkedTracks
        : originallyLinkedTracks;
    const linkedTracks = linkedItems
        .map((id) => trackLinkingInfo?.items.find((item) => item.id === id))
        .filter(filterNotEmpty);
    const unlinkedTracks =
        trackLinkingInfo?.items?.filter((item) => !linkedItems.includes(item.id)) ?? [];

    const [isSaving, setIsSaving] = useState(false);

    const linkLocationTrack = (trackId: LocationTrackId) => {
        if (operationalPointTrackLinkingState) {
            const newLinkedIds = deduplicate([
                ...operationalPointTrackLinkingState.linkedTracks,
                trackId,
            ]);
            delegates.setOperationalPointLinkedTracks(newLinkedIds);
        }
    };
    const unlinkLocationTrack = (trackId: LocationTrackId) => {
        if (operationalPointTrackLinkingState) {
            const newLinkedIds = operationalPointTrackLinkingState.linkedTracks.filter(
                (id) => id !== trackId,
            );
            delegates.setOperationalPointLinkedTracks(newLinkedIds);
        }
    };

    const handleSave = async () => {
        setIsSaving(true);
        try {
            if (operationalPointTrackLinkingState) {
                const switchesToLink = linkedItems.filter(
                    (sw) => !originallyLinkedTracks.includes(sw),
                );
                const switchesToUnlink = originallyLinkedTracks.filter(
                    (sw) => !operationalPointTrackLinkingState.linkedTracks.includes(sw),
                );

                const linkedNames =
                    switchesToLink.length > 0
                        ? await doLinkingOperationAndFetchNames(
                              layoutContext,
                              linkLocationTracksToOperationalPoint(
                                  layoutContext.branch,
                                  switchesToLink,
                                  operationalPoint.id,
                              ),
                          )
                        : [];
                const unlinkedNames =
                    switchesToUnlink.length > 0
                        ? await doLinkingOperationAndFetchNames(
                              layoutContext,
                              unlinkLocationTracksFromOperationalPoint(
                                  layoutContext.branch,
                                  switchesToUnlink,
                                  operationalPoint.id,
                              ),
                          )
                        : [];

                await updateAllChangeTimes();
                await getTracksForOperationalPoint(
                    layoutContext,
                    operationalPointTrackLinkingState.operationalPoint,
                );
                delegates.stopLinking();

                if (linkedNames.length > 0 || unlinkedNames.length > 0) {
                    const bodyInfo = linkingSummaryTranslationInfo(linkedNames, unlinkedNames);

                    Snackbar.success(
                        t(`tool-panel.operational-point.track-links.linking-success-toast-header`, {
                            operationalPointName: operationalPoint.name,
                        }),
                        t(
                            `tool-panel.operational-point.track-links.${bodyInfo.translationKey}`,
                            bodyInfo.params,
                        ),
                    );
                }
            }
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <Infobox
            title={t('tool-panel.operational-point.track-links.infobox-header', {
                count: linkedTracks.length,
            })}
            contentVisible={contentVisible}
            onContentVisibilityChange={() => onVisibilityChange('tracks')}>
            <InfoboxContent>
                <ProgressIndicatorWrapper
                    indicator={ProgressIndicatorType.Area}
                    inProgress={trackLinkingInfoFetchStatus !== LoaderStatus.Ready}>
                    {operationalPoint.polygon || linkedItems.length > 0 || isEditing ? (
                        <React.Fragment>
                            {!operationalPoint.polygon && linkedItems.length > 0 && (
                                <InfoboxContentSpread>
                                    <MessageBox type={MessageBoxType.ERROR}>
                                        {t(
                                            'tool-panel.operational-point.track-links.no-area-but-has-tracks-linked',
                                        )}
                                    </MessageBox>
                                </InfoboxContentSpread>
                            )}
                            <OperationalPointTracksDirectionInfobox
                                tracksInOperationalPointPolygon={tracksInOperationalPointPolygon}
                                tracks={linkedTracks}
                                linkingDirection={'unlinking'}
                                isEditing={isEditing}
                                linkingAction={unlinkLocationTrack}
                                massLinkingAction={() =>
                                    delegates.setOperationalPointLinkedTracks([])
                                }
                                onSelectLocationTrack={onSelectLocationTrack}
                            />
                            <OperationalPointTracksDirectionInfobox
                                tracksInOperationalPointPolygon={tracksInOperationalPointPolygon}
                                tracks={unlinkedTracks}
                                linkingDirection={'linking'}
                                isEditing={isEditing}
                                linkingAction={linkLocationTrack}
                                massLinkingAction={() =>
                                    delegates.setOperationalPointLinkedTracks(
                                        trackLinkingInfo?.items?.map((lt) => lt.id) ?? [],
                                    )
                                }
                                onSelectLocationTrack={onSelectLocationTrack}
                            />
                            <div
                                className={
                                    styles['operational-point-linking-infobox__edit-buttons']
                                }>
                                {!isEditing ? (
                                    <Button
                                        variant={ButtonVariant.SECONDARY}
                                        size={ButtonSize.SMALL}
                                        disabled={
                                            layoutContext.publicationState === 'OFFICIAL' ||
                                            !!linkingState
                                        }
                                        title={
                                            layoutContext.publicationState === 'OFFICIAL'
                                                ? t(
                                                      'tool-panel.disabled.activity-disabled-in-official-mode',
                                                  )
                                                : ''
                                        }
                                        onClick={() =>
                                            delegates.startLinkingOperationalPointTracks({
                                                operationalPoint: operationalPoint.id,
                                                linkedTracks: originallyLinkedTracks,
                                            })
                                        }>
                                        {t('tool-panel.operational-point.edit-links')}
                                    </Button>
                                ) : (
                                    <>
                                        <Button
                                            variant={ButtonVariant.SECONDARY}
                                            size={ButtonSize.SMALL}
                                            disabled={isSaving}
                                            onClick={() => delegates.stopLinking()}>
                                            {t('tool-panel.operational-point.cancel-editing-links')}
                                        </Button>
                                        <Button
                                            variant={ButtonVariant.PRIMARY}
                                            size={ButtonSize.SMALL}
                                            disabled={isSaving}
                                            isProcessing={isSaving}
                                            onClick={handleSave}>
                                            {t('tool-panel.operational-point.save-links')}
                                        </Button>
                                    </>
                                )}
                            </div>
                        </React.Fragment>
                    ) : layoutContext.publicationState === 'DRAFT' ? (
                        <InfoboxContentSpread>
                            <MessageBox>
                                {t('tool-panel.operational-point.track-links.no-area')}
                            </MessageBox>
                        </InfoboxContentSpread>
                    ) : (
                        <InfoboxText
                            value={t('tool-panel.operational-point.track-links.no-tracks')}
                        />
                    )}
                </ProgressIndicatorWrapper>
            </InfoboxContent>
        </Infobox>
    );
};
