import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import React from 'react';
import { useTranslation } from 'react-i18next';
import Infobox from 'tool-panel/infobox/infobox';
import {
    LayoutLocationTrack,
    OperationalPoint,
    OperationalPointId,
} from 'track-layout/track-layout-model';
import { useLocationTrackInfoboxExtras } from 'track-layout/track-layout-react-utils';
import { LayoutContext, TrackMeter } from 'common/common-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { unlinkLocationTracksFromOperationalPoint } from 'track-layout/layout-location-track-api';
import { updateAllChangeTimes } from 'common/change-time-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import styles from './location-track-operational-point-links-infobox.scss';
import { ChangeTimes } from 'common/common-slice';
import NavigableTrackMeter from 'geoviite-design-lib/track-meter/navigable-track-meter';
import { OperationalPointBadge } from 'geoviite-design-lib/operational-point/operational-point-badge';
import { OnSelectOptions } from 'selection/selection-model';
import { ShowMoreButton } from 'show-more-button/show-more-button';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { getManyOperationalPoints } from 'track-layout/layout-operational-point-api';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import infoboxStyles from 'tool-panel/infobox/infobox.module.scss';
import { createClassName } from 'vayla-design-lib/utils';

const maxOperationalPointsToDisplay = 10;

type LocationTrackOperationalPointLinksInfoboxProps = {
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
    locationTrack: LayoutLocationTrack;
    layoutContext: LayoutContext;
    changeTimes: ChangeTimes;
    onSelect: (items: OnSelectOptions) => void;
};

export const LocationTrackOperationalPointLinksInfobox: React.FC<
    LocationTrackOperationalPointLinksInfoboxProps
> = ({
    contentVisible,
    onContentVisibilityChange,
    locationTrack,
    layoutContext,
    changeTimes,
    onSelect,
}) => {
    const { t } = useTranslation();
    const [showingDialogToDetachOp, setShowingDialogToDetachOp] = React.useState<
        { id: OperationalPointId; name: string } | undefined
    >(undefined);
    const [isDetaching, setIsDetaching] = React.useState(false);

    const detachOperationalPoint = () => {
        if (showingDialogToDetachOp === undefined) return;
        setIsDetaching(true);
        unlinkLocationTracksFromOperationalPoint(
            layoutContext.branch,
            [locationTrack.id],
            showingDialogToDetachOp.id,
        )
            .then(async () => {
                await updateAllChangeTimes();
                setShowingDialogToDetachOp(undefined);
                Snackbar.success(
                    t(
                        'tool-panel.location-track.detach-operational-point-links-dialog.success-toast',
                        {
                            operationalPointName: showingDialogToDetachOp.name,
                            trackName: locationTrack.name,
                        },
                    ),
                );
            })
            .finally(() => setIsDetaching(false));
    };

    const [infoboxExtras, infoboxExtrasLoadStatus] = useLocationTrackInfoboxExtras(
        locationTrack.id,
        layoutContext,
        changeTimes,
    );
    const operationalPointExtras = infoboxExtras?.operationalPoints ?? [];
    const opIds = operationalPointExtras.map((op) => op.operationalPointId);

    const [operationalPoints, linkedPointFetchStatus] = useLoaderWithStatus(
        () => getManyOperationalPoints(opIds, layoutContext, changeTimes.operationalPoints),
        [
            locationTrack.id,
            JSON.stringify(opIds),
            layoutContext.branch,
            layoutContext.publicationState,
            changeTimes.operationalPoints,
        ],
    );

    const [showAll, setShowAll] = React.useState(false);

    return (
        <>
            <Infobox
                title={t('tool-panel.location-track.operational-point-links.heading')}
                qa-id={'location-track-operational-point-links-infobox'}
                contentVisible={contentVisible}
                onContentVisibilityChange={onContentVisibilityChange}>
                <ProgressIndicatorWrapper
                    indicator={ProgressIndicatorType.Area}
                    inProgress={
                        infoboxExtrasLoadStatus !== LoaderStatus.Ready ||
                        linkedPointFetchStatus !== LoaderStatus.Ready
                    }
                    inline={true}>
                    <InfoboxContent>
                        {!!operationalPoints && operationalPoints.length > 0 ? (
                            <React.Fragment>
                                <div
                                    className={
                                        styles[
                                            'location-track-operational-point-links-infobox-list'
                                        ]
                                    }>
                                    {(showAll
                                        ? operationalPoints
                                        : operationalPoints.slice(0, maxOperationalPointsToDisplay)
                                    ).map((operationalPoint) => (
                                        <LocationTrackOperationalPointLink
                                            key={operationalPoint.id}
                                            operationalPoint={operationalPoint}
                                            address={
                                                operationalPointExtras?.find(
                                                    (op) =>
                                                        op.operationalPointId ===
                                                        operationalPoint.id,
                                                )?.displayAddress
                                            }
                                            layoutContext={layoutContext}
                                            setShowingDialogToDetachOp={setShowingDialogToDetachOp}
                                            onSelect={onSelect}
                                        />
                                    ))}
                                </div>
                                {operationalPoints.length > maxOperationalPointsToDisplay && (
                                    <ShowMoreButton
                                        expanded={showAll}
                                        onShowMore={() => setShowAll(!showAll)}
                                        showMoreText={t(
                                            'tool-panel.location-track.operational-point-links.show-more',
                                            {
                                                count: operationalPoints.length,
                                            },
                                        )}
                                    />
                                )}
                            </React.Fragment>
                        ) : (
                            <p className={'infobox__text'}>
                                {t(
                                    'tool-panel.location-track.operational-point-links.no-operational-points',
                                )}
                            </p>
                        )}
                    </InfoboxContent>
                </ProgressIndicatorWrapper>
            </Infobox>
            {showingDialogToDetachOp && (
                <Dialog
                    title={t(
                        'tool-panel.location-track.detach-operational-point-links-dialog.title',
                    )}
                    variant={DialogVariant.DARK}
                    allowClose={true}
                    onClose={() => setShowingDialogToDetachOp(undefined)}
                    footerContent={
                        <>
                            <Button
                                onClick={() => setShowingDialogToDetachOp(undefined)}
                                variant={ButtonVariant.SECONDARY}
                                disabled={isDetaching}>
                                {t('button.cancel')}
                            </Button>
                            <div className={dialogStyles['dialog__footer-content--right-aligned']}>
                                <Button
                                    disabled={isDetaching}
                                    isProcessing={isDetaching}
                                    variant={ButtonVariant.PRIMARY_WARNING}
                                    onClick={() => detachOperationalPoint()}>
                                    {t(
                                        'tool-panel.location-track.detach-operational-point-links-dialog.detach-button',
                                    )}
                                </Button>
                            </div>
                        </>
                    }>
                    <div className={'dialog__text'}>
                        {t(
                            'tool-panel.location-track.detach-operational-point-links-dialog.message',
                            {
                                operationalPointName: showingDialogToDetachOp.name,
                                trackName: locationTrack.name,
                            },
                        )}
                    </div>
                </Dialog>
            )}
        </>
    );
};

type LocationTrackOperationalPointLinkProps = {
    operationalPoint: OperationalPoint;
    address: TrackMeter | undefined;
    layoutContext: LayoutContext;
    setShowingDialogToDetachOp: (detach: { id: OperationalPointId; name: string }) => void;
    onSelect: (items: OnSelectOptions) => void;
};

const LocationTrackOperationalPointLink: React.FC<LocationTrackOperationalPointLinkProps> = ({
    operationalPoint,
    address,
    layoutContext,
    setShowingDialogToDetachOp,
    onSelect,
}) => {
    const { t } = useTranslation();
    const remarkClassNames = createClassName(
        styles['location-track-operational-point-links-infobox-list__remark'],
        infoboxStyles['infobox__list-cell--strong'],
    );

    return (
        <>
            <div>
                <OperationalPointBadge
                    operationalPoint={operationalPoint}
                    onClick={() =>
                        onSelect({
                            operationalPoints: [operationalPoint.id],
                            selectedTab: { id: operationalPoint.id, type: 'OPERATIONAL_POINT' },
                        })
                    }
                />
            </div>
            <div className={infoboxStyles['infobox__list-cell--strong']}>
                {address === undefined ? (
                    t('tool-panel.location-track.operational-point-links.no-location')
                ) : (
                    <NavigableTrackMeter
                        trackMeter={address}
                        displayDecimals={false}
                        location={operationalPoint.location}
                    />
                )}
            </div>
            <div className={remarkClassNames}>
                {operationalPoint.state === 'DELETED' &&
                    t('tool-panel.location-track.operational-point-links.not-existing')}
            </div>
            <div>
                {layoutContext.publicationState === 'DRAFT' && (
                    <Button
                        size={ButtonSize.SMALL}
                        variant={ButtonVariant.GHOST}
                        onClick={() => setShowingDialogToDetachOp(operationalPoint)}>
                        {t('tool-panel.location-track.operational-point-links.detach')}
                    </Button>
                )}
            </div>
        </>
    );
};
