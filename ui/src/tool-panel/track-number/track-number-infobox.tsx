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
    useCoordinateSystem,
    useReferenceLineChangeTimes,
    useReferenceLineStartAndEnd,
    useTrackNumbers,
} from 'track-layout/track-layout-react-utils';
import { PublishType, TimeStamp } from 'common/common-model';
import { LinkingAlignment, LinkingState, LinkingType, LinkInterval } from 'linking/linking-model';
import { BoundingBox } from 'model/geometry';
import { updateReferenceLineGeometry } from 'linking/linking-api';
import TrackMeter from 'geoviite-design-lib/track-meter/track-meter';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Precision, roundToPrecision } from 'utils/rounding';
import { formatDateShort } from 'utils/date-utils';
import { TrackNumberEditDialogContainer } from './dialog/track-number-edit-dialog';
import { Icons } from 'vayla-design-lib/icon/Icon';
import TrackNumberDeleteConfirmationDialog from 'tool-panel/track-number/dialog/track-number-delete-confirmation-dialog';
import { TrackNumberGeometryInfobox } from 'tool-panel/track-number/track-number-geometry-infobox';
import { MapViewport } from 'map/map-model';
import { AssetValidationInfoboxContainer } from 'tool-panel/asset-validation-infobox-container';
import { TrackNumberInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { WriteAccessRequired } from 'user/write-access-required';
import { getEndLinkPoints } from 'track-layout/layout-map-api';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';

type TrackNumberInfoboxProps = {
    trackNumber: LayoutTrackNumber;
    referenceLine: LayoutReferenceLine | undefined;
    publishType: PublishType;
    linkingState?: LinkingState;
    showArea: (area: BoundingBox) => void;
    onUnselect: () => void;
    onStartReferenceLineGeometryChange: (alignment: LinkInterval) => void;
    onEndReferenceLineGeometryChange: () => void;
    viewport: MapViewport;
    referenceLineChangeTime: TimeStamp;
    visibilities: TrackNumberInfoboxVisibilities;
    onVisibilityChange: (visibilities: TrackNumberInfoboxVisibilities) => void;
    onHighlightItem: (item: HighlightedAlignment | undefined) => void;
};

const TrackNumberInfobox: React.FC<TrackNumberInfoboxProps> = ({
    trackNumber,
    referenceLine,
    publishType,
    linkingState,
    showArea,
    onStartReferenceLineGeometryChange,
    onEndReferenceLineGeometryChange,
    viewport,
    referenceLineChangeTime,
    visibilities,
    onVisibilityChange,
    onHighlightItem,
}: TrackNumberInfoboxProps) => {
    const { t } = useTranslation();
    const startAndEndPoints = useReferenceLineStartAndEnd(
        referenceLine?.id,
        publishType,
        referenceLineChangeTime,
    );
    const coordinateSystem = useCoordinateSystem(LAYOUT_SRID);
    const changeTimes = useReferenceLineChangeTimes(referenceLine?.id);
    const [showEditDialog, setShowEditDialog] = React.useState(false);
    const [canUpdate, setCanUpdate] = React.useState<boolean>();
    const [updatingLength, setUpdatingLength] = React.useState<boolean>(false);
    const [confirmingDraftDelete, setConfirmingDraftDelete] = React.useState<boolean>();
    const isOfficial = publishType === 'OFFICIAL';
    const officialTrackNumbers = useTrackNumbers('OFFICIAL');
    const isDeletable =
        officialTrackNumbers &&
        !officialTrackNumbers.find(
            (officialTrackNumber) => officialTrackNumber.id === trackNumber.id,
        );

    React.useEffect(() => {
        setCanUpdate(
            linkingState?.type === LinkingType.LinkingAlignment && linkingState.state === 'allSet',
        );
    }, [linkingState]);

    const updateAlignment = (state: LinkingAlignment) => {
        if (canUpdate && state.layoutAlignmentInterval.start && state.layoutAlignmentInterval.end) {
            setUpdatingLength(true);
            updateReferenceLineGeometry(state.layoutAlignmentId, {
                min: state.layoutAlignmentInterval.start.m,
                max: state.layoutAlignmentInterval.end.m,
            })
                .then(() => {
                    Snackbar.success(t('tool-panel.reference-line.end-points-updated'));
                    onEndReferenceLineGeometryChange();
                })
                .finally(() => setUpdatingLength(false));
        }
    };

    const showLocationTrackDeleteConfirmation = () => {
        setConfirmingDraftDelete(true);
    };

    const closeLocationTrackDeleteConfirmation = () => {
        setConfirmingDraftDelete(false);
    };

    const closeConfirmationAndUnselect = () => {
        closeLocationTrackDeleteConfirmation();
    };

    const visibilityChange = (key: keyof TrackNumberInfoboxVisibilities) => {
        onVisibilityChange({ ...visibilities, [key]: !visibilities[key] });
    };

    return (
        <React.Fragment>
            <Infobox
                contentVisible={visibilities.basic}
                onContentVisibilityChange={() => visibilityChange('basic')}
                title={t('tool-panel.track-number.general-title')}
                qa-id="track-number-infobox">
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.track-number.oid')}
                        value={trackNumber.externalId}
                    />
                    <InfoboxField
                        label={t('tool-panel.track-number.track-number')}
                        value={trackNumber.number}
                        onEdit={() => setShowEditDialog(true)}
                        iconDisabled={isOfficial}
                    />
                    <InfoboxField
                        label={t('tool-panel.track-number.state')}
                        value={<LayoutState state={trackNumber.state} />}
                        onEdit={() => setShowEditDialog(true)}
                        iconDisabled={isOfficial}
                    />
                    <InfoboxField
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
                            label={t('tool-panel.reference-line.start-location')}
                            value={<TrackMeter value={startAndEndPoints?.start?.address} />}
                            onEdit={() => setShowEditDialog(true)}
                            iconDisabled={isOfficial}
                        />
                        <InfoboxField
                            label={t('tool-panel.reference-line.end-location')}
                            value={<TrackMeter value={startAndEndPoints?.end?.address} />}
                        />
                        {linkingState === undefined && referenceLine && (
                            <WriteAccessRequired>
                                <InfoboxButtons>
                                    <Button
                                        variant={ButtonVariant.SECONDARY}
                                        size={ButtonSize.SMALL}
                                        onClick={() => {
                                            getEndLinkPoints(
                                                referenceLine.id,
                                                publishType,
                                                'REFERENCE_LINE',
                                                referenceLineChangeTime,
                                            ).then(onStartReferenceLineGeometryChange);
                                        }}>
                                        {t('tool-panel.location-track.modify-start-or-end')}
                                    </Button>
                                </InfoboxButtons>
                            </WriteAccessRequired>
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
                            label={t('tool-panel.reference-line.true-length')}
                            value={
                                roundToPrecision(
                                    referenceLine?.length || 0,
                                    Precision.alignmentLengthMeters,
                                ) + ' m'
                            }
                        />
                        <InfoboxField
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
                <TrackNumberGeometryInfobox
                    contentVisible={visibilities.geometry}
                    onContentVisibilityChange={() => visibilityChange('geometry')}
                    trackNumberId={trackNumber.id}
                    publishType={publishType}
                    viewport={viewport}
                    onHighlightItem={onHighlightItem}
                />
            )}
            {trackNumber.draftType !== 'NEW_DRAFT' && (
                <AssetValidationInfoboxContainer
                    contentVisible={visibilities.validation}
                    onContentVisibilityChange={() => visibilityChange('validation')}
                    id={trackNumber.id}
                    type={'TRACK_NUMBER'}
                    publishType={publishType}
                    changeTime={referenceLineChangeTime}
                />
            )}
            {changeTimes && (
                <Infobox
                    contentVisible={visibilities.log}
                    onContentVisibilityChange={() => visibilityChange('log')}
                    title={t('tool-panel.reference-line.change-info-heading')}
                    qa-id="track-number-log-infobox">
                    <InfoboxContent>
                        <InfoboxField
                            label={t('tool-panel.reference-line.created')}
                            value={formatDateShort(changeTimes.created)}
                        />
                        <InfoboxField
                            label={t('tool-panel.reference-line.changed')}
                            value={formatDateShort(changeTimes.changed)}
                        />
                        {isDeletable && (
                            <InfoboxButtons>
                                <Button
                                    onClick={() => showLocationTrackDeleteConfirmation()}
                                    icon={Icons.Delete}
                                    variant={ButtonVariant.WARNING}
                                    size={ButtonSize.SMALL}>
                                    {t('tool-panel.location-track.delete-draft')}
                                </Button>
                            </InfoboxButtons>
                        )}
                    </InfoboxContent>
                </Infobox>
            )}
            {confirmingDraftDelete && (
                <TrackNumberDeleteConfirmationDialog
                    id={trackNumber.id}
                    onClose={closeConfirmationAndUnselect}
                    onCancel={closeLocationTrackDeleteConfirmation}
                />
            )}
            {showEditDialog && (
                <TrackNumberEditDialogContainer
                    editTrackNumberId={trackNumber.id}
                    onClose={() => setShowEditDialog(false)}
                />
            )}
        </React.Fragment>
    );
};

export default TrackNumberInfobox;
