import * as React from 'react';
import { useTranslation } from 'react-i18next';
import Infobox from 'tool-panel/infobox/infobox';
import {
    OperationalPointInfoboxVisibilities,
    TrackLayoutState,
} from 'track-layout/track-layout-slice';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { OperationalPointOid } from 'track-layout/oid';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { draftLayoutContext, LayoutContext } from 'common/common-model';
import {
    OperationalPoint,
    operationalPointRinfTypeToTypeCode,
} from 'track-layout/track-layout-model';
import LayoutState from 'geoviite-design-lib/layout-state/layout-state';
import { useLoader } from 'utils/react-utils';
import { getOperationalPointChangeTimes } from 'track-layout/layout-operational-point-api';
import { ChangeTimes } from 'common/common-slice';
import { formatDateShort } from 'utils/date-utils';
import { refreshOperationalPointSelection } from 'track-layout/track-layout-react-utils';
import { OnSelectOptions, OptionalUnselectableItemCollections } from 'selection/selection-model';
import { OperationalPointEditDialogContainer } from 'tool-panel/operational-point/operational-point-edit-dialog-container';
import { OperationalPointLocationInfobox } from 'tool-panel/operational-point/operational-point-location-infobox';
import { AssetValidationInfoboxContainer } from 'tool-panel/asset-validation-infobox-container';
import { OperationalPointSwitchesInfobox } from 'tool-panel/operational-point/operational-point-switches-infobox';
import { EnvRestricted } from 'environment/env-restricted';
import { useHasPublicationLog } from 'publication/publication-utils';
import { getLocationTrack } from 'track-layout/layout-location-track-api';
import { SearchItemType, SearchItemValue } from 'asset-search/search-dropdown';
import { useAppNavigate } from 'common/navigate';
import { OperationalPointTracksInfobox } from 'tool-panel/operational-point/operational-point-tracks-infobox';

type OperationalPointInfoboxProps = {
    operationalPoint: OperationalPoint;
    layoutContext: LayoutContext;
    changeTimes: ChangeTimes;
    layoutState: TrackLayoutState;
    visibilities: OperationalPointInfoboxVisibilities;
    onVisibilityChange: (visibilities: OperationalPointInfoboxVisibilities) => void;
    onDataChange: () => void;
    onSelect: (items: OnSelectOptions) => void;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    onShowOnMap: () => void;
    onStartPlacingLocation: () => void;
    onStopPlacingLocation: () => void;
    onStartPlacingArea: () => void;
    onStopPlacingArea: () => void;
    onClearArea: () => void;
    startFreshSpecificItemPublicationLogSearch: (item: SearchItemValue<SearchItemType>) => void;
};

export const OperationalPointInfobox: React.FC<OperationalPointInfoboxProps> = ({
    operationalPoint,
    visibilities,
    changeTimes,
    layoutState,
    onVisibilityChange,
    layoutContext,
    onDataChange,
    onSelect,
    onUnselect,
    onShowOnMap,
    onStartPlacingLocation,
    onStopPlacingLocation,
    onStartPlacingArea,
    onStopPlacingArea,
    onClearArea,
    startFreshSpecificItemPublicationLogSearch,
}) => {
    const { t } = useTranslation();
    const navigate = useAppNavigate();

    const visibilityChange = (key: keyof OperationalPointInfoboxVisibilities) => {
        onVisibilityChange({ ...visibilities, [key]: !visibilities[key] });
    };

    const changeInfo = useLoader(
        () => getOperationalPointChangeTimes(operationalPoint.id, layoutContext),
        [operationalPoint.id, changeTimes.operationalPoints],
    );

    const [editDialogOpen, setEditDialogOpen] = React.useState(false);
    const isExternal = operationalPoint.origin === 'RATKO';

    const openEditDialog = () => {
        setEditDialogOpen(true);
        onDataChange();
    };

    const closeEditDialog = () => {
        setEditDialogOpen(false);
        onDataChange();
    };

    const handleOperationalPointSave = refreshOperationalPointSelection(
        draftLayoutContext(layoutContext),
        onSelect,
        onUnselect,
    );

    const hasPublicationLog = useHasPublicationLog(
        operationalPoint.id,
        getLocationTrack,
        changeInfo?.changed,
    );

    const openPublicationLogButtonTitle = hasPublicationLog
        ? undefined
        : t('tool-panel.location-track.publication-log-unavailable');

    const openPublicationLog = React.useCallback(() => {
        startFreshSpecificItemPublicationLogSearch({
            type: SearchItemType.OPERATIONAL_POINT,
            operationalPoint,
        });
        navigate('publication-search');
    }, [operationalPoint, changeInfo]);

    return (
        <React.Fragment>
            <Infobox
                contentVisible={visibilities.basic}
                onContentVisibilityChange={() => visibilityChange('basic')}
                title={t('tool-panel.operational-point.basic-info-heading')}
                qa-id="operational-point-infobox-basic"
                onEdit={openEditDialog}
                iconDisabled={
                    layoutContext.publicationState === 'OFFICIAL' || !!layoutState.linkingState
                }>
                <InfoboxContent>
                    <InfoboxField
                        qaId="operational-point-oid"
                        label={t('tool-panel.operational-point.identifier')}
                        value={
                            <OperationalPointOid
                                id={operationalPoint.id}
                                changeTimes={changeTimes}
                                branch={layoutContext.branch}
                                getFallbackTextIfNoOid={() =>
                                    t('tool-panel.operational-point.unpublished')
                                }
                            />
                        }
                    />
                    <InfoboxField
                        qaId="operational-point-name"
                        label={t('tool-panel.operational-point.name')}
                        value={operationalPoint.name}
                    />
                    <InfoboxField
                        qaId="operational-point-abbreviation"
                        label={t('tool-panel.operational-point.abbreviation')}
                        value={operationalPoint.abbreviation}
                    />
                    <InfoboxField
                        label={t('tool-panel.operational-point.state')}
                        value={<LayoutState state={operationalPoint.state} />}
                    />
                    <InfoboxField
                        label={t('tool-panel.operational-point.source')}
                        value={t(`enum.OperationalPointOrigin.${operationalPoint.origin}`)}
                    />
                    {isExternal && (
                        <InfoboxField
                            label={t('tool-panel.operational-point.type-raide')}
                            value={t(
                                `enum.OperationalPointRaideType.${operationalPoint.raideType}`,
                            )}
                        />
                    )}
                    <InfoboxField
                        label={t('tool-panel.operational-point.type-rinf')}
                        value={
                            operationalPoint.rinfType
                                ? t('enum.rinf-type-full', {
                                      rinfType: operationalPoint.rinfType,
                                      rinfTypeCode: operationalPointRinfTypeToTypeCode(
                                          operationalPoint.rinfType,
                                      ),
                                  })
                                : t('tool-panel.operational-point.unset')
                        }
                    />
                    <InfoboxField
                        label={t('tool-panel.operational-point.uic-code')}
                        value={operationalPoint.uicCode}
                    />
                </InfoboxContent>
            </Infobox>
            <OperationalPointLocationInfobox
                operationalPoint={operationalPoint}
                layoutContext={layoutContext}
                layoutState={layoutState}
                visibilities={visibilities}
                visibilityChange={visibilityChange}
                onShowOnMap={onShowOnMap}
                onStartPlacingLocation={onStartPlacingLocation}
                onStopPlacingLocation={onStopPlacingLocation}
                onStartPlacingArea={onStartPlacingArea}
                onStopPlacingArea={onStopPlacingArea}
                onClearArea={onClearArea}
            />
            <EnvRestricted restrictTo={'dev'}>
                {operationalPoint.polygon && (
                    <>
                        <OperationalPointSwitchesInfobox
                            contentVisible={visibilities.switches}
                            onVisibilityChange={visibilityChange}
                            layoutContext={layoutContext}
                            operationalPoint={operationalPoint}
                            changeTimes={changeTimes}
                        />
                        <OperationalPointTracksInfobox
                            contentVisible={visibilities.tracks}
                            onVisibilityChange={visibilityChange}
                            layoutContext={layoutContext}
                            operationalPoint={operationalPoint}
                            changeTimes={changeTimes}
                        />
                    </>
                )}
            </EnvRestricted>
            <AssetValidationInfoboxContainer
                contentVisible={visibilities.validation}
                onContentVisibilityChange={() => visibilityChange('validation')}
                idAndType={{ id: operationalPoint.id, type: 'OPERATIONAL_POINT' }}
                layoutContext={layoutContext}
                changeTime={changeTimes.operationalPoints}
            />
            <Infobox
                contentVisible={visibilities.log}
                onContentVisibilityChange={() => visibilityChange('log')}
                title={t('tool-panel.operational-point.log-heading')}
                qa-id="operational-point-infobox-log">
                <InfoboxContent>
                    {changeInfo && (
                        <React.Fragment>
                            <InfoboxField
                                label={t('tool-panel.operational-point.created')}
                                value={formatDateShort(changeInfo.created)}
                            />
                            <InfoboxField
                                label={t('tool-panel.operational-point.modified')}
                                value={
                                    changeInfo?.changed
                                        ? formatDateShort(changeInfo?.changed)
                                        : t('tool-panel.unmodified-in-geoviite')
                                }
                            />
                            <InfoboxButtons>
                                <Button
                                    variant={ButtonVariant.SECONDARY}
                                    size={ButtonSize.SMALL}
                                    title={openPublicationLogButtonTitle}
                                    disabled={!hasPublicationLog}
                                    onClick={openPublicationLog}>
                                    {t('tool-panel.show-in-publication-log')}
                                </Button>
                            </InfoboxButtons>
                        </React.Fragment>
                    )}
                </InfoboxContent>
            </Infobox>
            {editDialogOpen && (
                <OperationalPointEditDialogContainer
                    operationalPointId={operationalPoint.id}
                    layoutContext={layoutContext}
                    onSave={handleOperationalPointSave}
                    onClose={closeEditDialog}
                />
            )}
        </React.Fragment>
    );
};
