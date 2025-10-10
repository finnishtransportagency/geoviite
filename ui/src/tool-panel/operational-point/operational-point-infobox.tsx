import * as React from 'react';
import { useTranslation } from 'react-i18next';
import Infobox from 'tool-panel/infobox/infobox';
import { OperationalPointInfoboxVisibilities } from 'track-layout/track-layout-slice';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { OperationalPointOid } from 'track-layout/oid';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { draftLayoutContext, LayoutContext } from 'common/common-model';
import { InternalOperationalPointEditDialog } from 'tool-panel/operational-point/internal-operational-point-edit-dialog';
import { ExternalOperationalPointEditDialog } from 'tool-panel/operational-point/external-operational-point-edit-dialog';
import { OperationalPoint } from 'track-layout/track-layout-model';
import LayoutState from 'geoviite-design-lib/layout-state/layout-state';
import { formatToTM35FINString } from 'utils/geography-utils';
import { useLoader } from 'utils/react-utils';
import { getOperationalPointChangeTimes } from 'track-layout/layout-operational-point-api';
import { ChangeTimes } from 'common/common-slice';
import { formatDateShort } from 'utils/date-utils';
import { refreshOperationalPointSelection } from 'track-layout/track-layout-react-utils';
import { OnSelectOptions, OptionalUnselectableItemCollections } from 'selection/selection-model';

type OperationalPointInfoboxProps = {
    operationalPoint: OperationalPoint;
    layoutContext: LayoutContext;
    changeTimes: ChangeTimes;
    visibilities: OperationalPointInfoboxVisibilities;
    onVisibilityChange: (visibilities: OperationalPointInfoboxVisibilities) => void;
    onDataChange: () => void;
    onSelect: (items: OnSelectOptions) => void;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
};

export const OperationalPointInfobox: React.FC<OperationalPointInfoboxProps> = ({
    operationalPoint,
    visibilities,
    changeTimes,
    onVisibilityChange,
    layoutContext,
    onDataChange,
    onSelect,
    onUnselect,
}) => {
    const { t } = useTranslation();
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

    return (
        <React.Fragment>
            <Infobox
                contentVisible={visibilities.basic}
                onContentVisibilityChange={() => visibilityChange('basic')}
                title={t('tool-panel.operational-point.basic-info-heading')}
                qa-id="operational-point-infobox-basic"
                onEdit={openEditDialog}
                iconDisabled={layoutContext.publicationState === 'OFFICIAL'}>
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.operational-point.source')}
                        value={t(`enum.OperationalPointOrigin.${operationalPoint.origin}`)}
                    />
                    <InfoboxField
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
                        label={t('tool-panel.operational-point.name')}
                        value={operationalPoint.name}
                    />
                    <InfoboxField
                        label={t('tool-panel.operational-point.abbreviation')}
                        value={operationalPoint.abbreviation}
                    />
                    <InfoboxField
                        label={t('tool-panel.operational-point.state')}
                        value={<LayoutState state={operationalPoint.state} />}
                    />
                    {isExternal && (
                        <InfoboxField
                            label={t('tool-panel.operational-point.type-raide')}
                            value={t(`enum.RaideType.${operationalPoint.raideType}`)}
                        />
                    )}
                    <InfoboxField
                        label={t('tool-panel.operational-point.type-rinf')}
                        value={
                            operationalPoint.rinfType
                                ? t('enum.rinf-type-full', {
                                      rinfType: operationalPoint.rinfType,
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
            <Infobox
                contentVisible={visibilities.location}
                onContentVisibilityChange={() => visibilityChange('location')}
                title={t('tool-panel.operational-point.location-heading')}
                qa-id="operational-point-infobox-location">
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.operational-point.location')}
                        value={
                            operationalPoint.location
                                ? formatToTM35FINString(operationalPoint.location)
                                : '-'
                        }
                    />
                    <InfoboxButtons>
                        <Button
                            variant={ButtonVariant.SECONDARY}
                            size={ButtonSize.SMALL}
                            disabled={!operationalPoint.location}>
                            {t('tool-panel.operational-point.focus-on-map')}
                        </Button>
                    </InfoboxButtons>
                    <InfoboxButtons>
                        <Button
                            variant={ButtonVariant.SECONDARY}
                            size={ButtonSize.SMALL}
                            disabled={isExternal}
                            title={
                                isExternal
                                    ? t(
                                          'tool-panel.operational-point.cannot-set-location-for-external',
                                      )
                                    : undefined
                            }>
                            {t('tool-panel.operational-point.set-location')}
                        </Button>
                    </InfoboxButtons>
                    <InfoboxButtons>
                        <Button variant={ButtonVariant.SECONDARY} size={ButtonSize.SMALL}>
                            {t('tool-panel.operational-point.set-area')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>
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
                                    disabled={true}>
                                    {t('tool-panel.show-in-publication-log')}
                                </Button>
                            </InfoboxButtons>
                        </React.Fragment>
                    )}
                </InfoboxContent>
            </Infobox>
            {editDialogOpen &&
                (isExternal ? (
                    <ExternalOperationalPointEditDialog
                        operationalPoint={operationalPoint}
                        layoutContext={layoutContext}
                        onSave={handleOperationalPointSave}
                        onClose={closeEditDialog}
                    />
                ) : (
                    <InternalOperationalPointEditDialog
                        operationalPoint={operationalPoint}
                        layoutContext={layoutContext}
                        onSave={handleOperationalPointSave}
                        onClose={closeEditDialog}
                    />
                ))}
        </React.Fragment>
    );
};
