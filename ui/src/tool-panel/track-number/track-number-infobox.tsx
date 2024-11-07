import * as React from 'react';
import styles from './track-number-infobox.scss';
import Infobox from 'tool-panel/infobox/infobox';
import {
    LAYOUT_SRID,
    LayoutReferenceLine,
    LayoutTrackNumber,
} from 'track-layout/track-layout-model';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { useTranslation } from 'react-i18next';
import LayoutState from 'geoviite-design-lib/layout-state/layout-state';
import { formatToTM35FINString } from 'utils/geography-utils';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import {
    refreshTrackNumberSelection,
    useCoordinateSystem,
    useReferenceLineChangeTimes,
    useReferenceLineStartAndEnd,
    useTrackNumberChangeTimes,
} from 'track-layout/track-layout-react-utils';
import { LinkingAlignment, LinkingState, LinkingType, LinkInterval } from 'linking/linking-model';
import { BoundingBox } from 'model/geometry';
import { updateReferenceLineGeometry } from 'linking/linking-api';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Precision, roundToPrecision } from 'utils/rounding';
import { formatDateShort, getMaxTimestamp, getMinTimestamp } from 'utils/date-utils';
import { TrackNumberEditDialogContainer } from './dialog/track-number-edit-dialog';
import { TrackNumberGeometryInfobox } from 'tool-panel/track-number/track-number-geometry-infobox';
import { MapViewport } from 'map/map-model';
import { AssetValidationInfoboxContainer } from 'tool-panel/asset-validation-infobox-container';
import { TrackNumberInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { getEndLinkPoints } from 'track-layout/layout-map-api';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { ChangeTimes } from 'common/common-slice';
import { OnSelectFunction, OptionalUnselectableItemCollections } from 'selection/selection-model';
import NavigableTrackMeter from 'geoviite-design-lib/track-meter/navigable-track-meter';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_LAYOUT, VIEW_GEOMETRY } from 'user/user-model';
import { draftLayoutContext, LayoutContext } from 'common/common-model';

type TrackNumberInfoboxProps = {
    trackNumber: LayoutTrackNumber;
    referenceLine: LayoutReferenceLine | undefined;
    layoutContext: LayoutContext;
    linkingState?: LinkingState;
    showArea: (area: BoundingBox) => void;
    onSelect: OnSelectFunction;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    onStartReferenceLineGeometryChange: (alignment: LinkInterval) => void;
    onEndReferenceLineGeometryChange: () => void;
    viewport: MapViewport;
    changeTimes: ChangeTimes;
    visibilities: TrackNumberInfoboxVisibilities;
    onVisibilityChange: (visibilities: TrackNumberInfoboxVisibilities) => void;
    onHighlightItem: (item: HighlightedAlignment | undefined) => void;
};

const TrackNumberInfobox: React.FC<TrackNumberInfoboxProps> = ({
    trackNumber,
    referenceLine,
    layoutContext,
    linkingState,
    showArea,
    onSelect,
    onUnselect,
    onStartReferenceLineGeometryChange,
    onEndReferenceLineGeometryChange,
    viewport,
    changeTimes,
    visibilities,
    onVisibilityChange,
    onHighlightItem,
}: TrackNumberInfoboxProps) => {
    const { t } = useTranslation();
    const trackNumberChangeTime = getMaxTimestamp(
        changeTimes.layoutTrackNumber,
        changeTimes.layoutReferenceLine,
    );
    const startAndEndPoints = useReferenceLineStartAndEnd(
        referenceLine?.id,
        layoutContext,
        trackNumberChangeTime,
    );
    const coordinateSystem = useCoordinateSystem(LAYOUT_SRID);
    const tnChangeTimes = useTrackNumberChangeTimes(trackNumber?.id, layoutContext);
    const rlChangeTimes = useReferenceLineChangeTimes(referenceLine?.id, layoutContext);
    const createdTime =
        tnChangeTimes?.created && rlChangeTimes?.created
            ? getMinTimestamp(tnChangeTimes.created, rlChangeTimes.created)
            : tnChangeTimes?.created || rlChangeTimes?.created;
    const changedTime =
        tnChangeTimes?.changed && rlChangeTimes?.changed
            ? getMaxTimestamp(tnChangeTimes.changed, rlChangeTimes.changed)
            : tnChangeTimes?.changed || rlChangeTimes?.changed;
    const [showEditDialog, setShowEditDialog] = React.useState(false);
    const [canUpdate, setCanUpdate] = React.useState<boolean>();
    const [updatingLength, setUpdatingLength] = React.useState<boolean>(false);
    const isOfficial = layoutContext.publicationState === 'OFFICIAL';

    React.useEffect(() => {
        setCanUpdate(
            linkingState?.type === LinkingType.LinkingAlignment && linkingState.state === 'allSet',
        );
    }, [linkingState]);

    const updateAlignment = (state: LinkingAlignment) => {
        if (
            canUpdate &&
            state.layoutAlignmentInterval.start &&
            state.layoutAlignmentInterval.end &&
            state.layoutAlignment.type === 'REFERENCE_LINE'
        ) {
            setUpdatingLength(true);
            updateReferenceLineGeometry(layoutContext.branch, state.layoutAlignment.id, {
                min: state.layoutAlignmentInterval.start.m,
                max: state.layoutAlignmentInterval.end.m,
            })
                .then(() => {
                    Snackbar.success('tool-panel.reference-line.end-points-updated');
                    onEndReferenceLineGeometryChange();
                })
                .finally(() => setUpdatingLength(false));
        }
    };

    const visibilityChange = (key: keyof TrackNumberInfoboxVisibilities) => {
        onVisibilityChange({ ...visibilities, [key]: !visibilities[key] });
    };

    const handleTrackNumberSave = refreshTrackNumberSelection(
        draftLayoutContext(layoutContext),
        onSelect,
        onUnselect,
    );

    return (
        <React.Fragment>
            <Infobox
                contentVisible={visibilities.basic}
                onContentVisibilityChange={() => visibilityChange('basic')}
                title={t('tool-panel.track-number.general-title')}
                qa-id="track-number-infobox">
                <InfoboxContent>
                    <InfoboxField
                        qaId="track-number-oid"
                        label={t('tool-panel.track-number.oid')}
                        value={trackNumber.externalId}
                    />
                    <InfoboxField
                        qaId="track-number-name"
                        label={t('tool-panel.track-number.track-number')}
                        value={trackNumber.number}
                        onEdit={() => setShowEditDialog(true)}
                        iconDisabled={isOfficial}
                    />
                    <InfoboxField
                        qaId="track-number-state"
                        label={t('tool-panel.track-number.state')}
                        value={<LayoutState state={trackNumber.state} />}
                        onEdit={() => setShowEditDialog(true)}
                        iconDisabled={isOfficial}
                    />
                    <InfoboxField
                        qaId="track-number-description"
                        label={t('tool-panel.track-number.description')}
                        value={trackNumber.description}
                        onEdit={() => setShowEditDialog(true)}
                        iconDisabled={isOfficial}
                    />
                </InfoboxContent>
            </Infobox>
            {startAndEndPoints && coordinateSystem && (
                <Infobox
                    contentVisible={visibilities.referenceLine}
                    onContentVisibilityChange={() => visibilityChange('referenceLine')}
                    title={t('tool-panel.reference-line.basic-info-heading')}
                    qa-id="reference-line-location-infobox">
                    <InfoboxContent>
                        <InfoboxField
                            qaId="track-number-start-track-meter"
                            label={t('tool-panel.reference-line.start-location')}
                            value={
                                <NavigableTrackMeter
                                    trackMeter={startAndEndPoints?.start?.address}
                                    location={startAndEndPoints?.start?.point}
                                />
                            }
                            onEdit={() => setShowEditDialog(true)}
                            iconDisabled={isOfficial}
                        />
                        <InfoboxField
                            qaId="track-number-end-track-meter"
                            label={t('tool-panel.reference-line.end-location')}
                            value={
                                <NavigableTrackMeter
                                    trackMeter={startAndEndPoints?.end?.address}
                                    location={startAndEndPoints?.end?.point}
                                />
                            }
                        />
                        {linkingState === undefined && referenceLine && (
                            <PrivilegeRequired privilege={EDIT_LAYOUT}>
                                <InfoboxButtons>
                                    <Button
                                        variant={ButtonVariant.SECONDARY}
                                        size={ButtonSize.SMALL}
                                        onClick={() => {
                                            getEndLinkPoints(
                                                referenceLine.id,
                                                layoutContext,
                                                'REFERENCE_LINE',
                                                changeTimes.layoutReferenceLine,
                                            ).then(onStartReferenceLineGeometryChange);
                                        }}>
                                        {t('tool-panel.location-track.modify-start-or-end')}
                                    </Button>
                                </InfoboxButtons>
                            </PrivilegeRequired>
                        )}
                        {linkingState?.type === LinkingType.LinkingAlignment && (
                            <React.Fragment>
                                <p
                                    className={
                                        styles['track-number-infobox__link-reference-line-guide']
                                    }>
                                    {t('tool-panel.location-track.choose-start-and-end-points')}
                                </p>
                                <InfoboxButtons>
                                    <Button
                                        variant={ButtonVariant.SECONDARY}
                                        size={ButtonSize.SMALL}
                                        disabled={updatingLength}
                                        onClick={onEndReferenceLineGeometryChange}>
                                        {t('button.cancel')}
                                    </Button>
                                    <Button
                                        size={ButtonSize.SMALL}
                                        disabled={updatingLength || !canUpdate}
                                        isProcessing={updatingLength}
                                        onClick={() => {
                                            updateAlignment(linkingState);
                                        }}>
                                        {t('tool-panel.location-track.ready')}
                                    </Button>
                                </InfoboxButtons>
                            </React.Fragment>
                        )}

                        <InfoboxField
                            qaId="track-number-true-length"
                            label={t('tool-panel.reference-line.true-length')}
                            value={
                                roundToPrecision(
                                    referenceLine?.length || 0,
                                    Precision.alignmentLengthMeters,
                                ) + ' m'
                            }
                        />
                        <InfoboxField
                            qaId="track-number-start-coordinates"
                            label={`${t('tool-panel.reference-line.start-coordinates')} ${
                                coordinateSystem.name
                            }`}
                            value={
                                startAndEndPoints?.start
                                    ? formatToTM35FINString(startAndEndPoints.start.point)
                                    : ''
                            }
                        />
                        <InfoboxField
                            qaId="track-number-end-coordinates"
                            label={`${t('tool-panel.reference-line.end-coordinates')} ${
                                coordinateSystem.name
                            }`}
                            value={
                                startAndEndPoints?.end
                                    ? formatToTM35FINString(startAndEndPoints.end.point)
                                    : ''
                            }
                        />
                        <InfoboxButtons>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                size={ButtonSize.SMALL}
                                qa-id="zoom-to-track-number"
                                title={
                                    !referenceLine?.boundingBox
                                        ? t('tool-panel.reference-line.layout.no-geometry')
                                        : ''
                                }
                                disabled={!referenceLine?.boundingBox}
                                onClick={() =>
                                    referenceLine?.boundingBox &&
                                    showArea(referenceLine.boundingBox)
                                }>
                                {t('tool-panel.reference-line.show-on-map')}
                            </Button>
                        </InfoboxButtons>
                    </InfoboxContent>
                </Infobox>
            )}
            {referenceLine && (
                <PrivilegeRequired privilege={VIEW_GEOMETRY}>
                    <TrackNumberGeometryInfobox
                        contentVisible={visibilities.geometry}
                        onContentVisibilityChange={() => visibilityChange('geometry')}
                        trackNumberId={trackNumber.id}
                        layoutContext={layoutContext}
                        viewport={viewport}
                        onHighlightItem={onHighlightItem}
                        changeTime={trackNumberChangeTime}
                    />
                </PrivilegeRequired>
            )}
            <AssetValidationInfoboxContainer
                contentVisible={visibilities.validation}
                onContentVisibilityChange={() => visibilityChange('validation')}
                idAndType={{ id: trackNumber.id, type: 'TRACK_NUMBER' }}
                layoutContext={layoutContext}
                changeTime={trackNumberChangeTime}
            />
            {createdTime && changedTime && (
                <Infobox
                    contentVisible={visibilities.log}
                    onContentVisibilityChange={() => visibilityChange('log')}
                    title={t('tool-panel.reference-line.change-info-heading')}
                    qa-id="track-number-log-infobox">
                    <InfoboxContent>
                        <InfoboxField
                            qaId="track-number-created-date"
                            label={t('tool-panel.created')}
                            value={formatDateShort(createdTime)}
                        />
                        <InfoboxField
                            qaId="track-number-changed-date"
                            label={t('tool-panel.changed')}
                            value={formatDateShort(changedTime)}
                        />
                    </InfoboxContent>
                </Infobox>
            )}
            {showEditDialog && (
                <TrackNumberEditDialogContainer
                    editTrackNumberId={trackNumber.id}
                    onClose={() => setShowEditDialog(false)}
                    onSave={handleTrackNumberSave}
                />
            )}
        </React.Fragment>
    );
};

export default TrackNumberInfobox;
