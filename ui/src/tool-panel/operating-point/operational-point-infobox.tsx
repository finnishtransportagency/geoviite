import * as React from 'react';
import { useTranslation } from 'react-i18next';
import Infobox from 'tool-panel/infobox/infobox';
import { OperatingPointInfoboxVisibilities } from 'track-layout/track-layout-slice';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { OperatingPointOid } from 'track-layout/oid';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { LayoutContext } from 'common/common-model';
import { InternalOperationalPointEditDialog } from 'tool-panel/operating-point/internal-operational-point-edit-dialog';
import { ExternalOperationalPointEditDialog } from 'tool-panel/operating-point/external-operational-point-edit-dialog';
import { OperationalPoint } from 'track-layout/track-layout-model';
import LayoutState from 'geoviite-design-lib/layout-state/layout-state';
import { formatToTM35FINString } from 'utils/geography-utils';
import { useLoader } from 'utils/react-utils';
import { getOperationalPointChangeTimes } from 'track-layout/layout-operating-point-api';
import { ChangeTimes } from 'common/common-slice';
import { formatDateShort } from 'utils/date-utils';

type OperatingPointInfoboxProps = {
    operationalPoint: OperationalPoint;
    layoutContext: LayoutContext;
    changeTimes: ChangeTimes;
    visibilities: OperatingPointInfoboxVisibilities;
    onVisibilityChange: (visibilities: OperatingPointInfoboxVisibilities) => void;
};

export const OperationalPointInfobox: React.FC<OperatingPointInfoboxProps> = ({
    operationalPoint,
    visibilities,
    changeTimes,
    onVisibilityChange,
    layoutContext,
}) => {
    const { t } = useTranslation();
    const visibilityChange = (key: keyof OperatingPointInfoboxVisibilities) => {
        onVisibilityChange({ ...visibilities, [key]: !visibilities[key] });
    };

    const changeInfo = useLoader(
        () => getOperationalPointChangeTimes(operationalPoint.id, layoutContext),
        [operationalPoint.id, changeTimes.operatingPoints],
    );

    const [editDialogOpen, setEditDialogOpen] = React.useState(false);
    const isExternal = operationalPoint.origin === 'RATKO';

    return (
        <React.Fragment>
            <Infobox
                contentVisible={visibilities.basic}
                onContentVisibilityChange={() => visibilityChange('basic')}
                title={t('tool-panel.operating-point.basic-info-heading')}
                qa-id="operating-point-infobox"
                onEdit={() => setEditDialogOpen(true)}
                iconDisabled={layoutContext.publicationState === 'OFFICIAL'}>
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.operating-point.source')}
                        value={isExternal ? 'Ratko' : 'Geoviite'}
                    />
                    <InfoboxField
                        label={t('tool-panel.operating-point.identifier')}
                        value={<OperatingPointOid />}
                    />
                    <InfoboxField
                        label={t('tool-panel.operating-point.name')}
                        value={operationalPoint.name}
                    />
                    <InfoboxField
                        label={t('tool-panel.operating-point.abbreviation')}
                        value={operationalPoint.abbreviation}
                    />
                    <InfoboxField
                        label={t('tool-panel.operating-point.state')}
                        value={<LayoutState state={operationalPoint.state} />}
                    />
                    {isExternal && (
                        <InfoboxField
                            label={t('tool-panel.operating-point.type-raide')}
                            value={t(`enum.RaideType.${operationalPoint.raideType}`)}
                        />
                    )}
                    <InfoboxField
                        label={t('tool-panel.operating-point.type-rinf')}
                        value={t('enum.rinf-type-full', {
                            rinfType: t(`enum.RinfType.${operationalPoint.rinfType}`),
                            rinfCode: operationalPoint.rinfType,
                        })}
                    />
                    <InfoboxField
                        label={t('tool-panel.operating-point.uic-code')}
                        value={operationalPoint.uicCode}
                    />
                </InfoboxContent>
            </Infobox>
            <Infobox
                contentVisible={visibilities.location}
                onContentVisibilityChange={() => visibilityChange('location')}
                title={t('tool-panel.operating-point.location-heading')}
                qa-id="operating-point-infobox">
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.operating-point.location')}
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
                            {t('tool-panel.operating-point.focus-on-map')}
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
                                          'tool-panel.operating-point.cannot-set-location-for-external',
                                      )
                                    : undefined
                            }>
                            {t('tool-panel.operating-point.set-location')}
                        </Button>
                    </InfoboxButtons>
                    <InfoboxButtons>
                        <Button variant={ButtonVariant.SECONDARY} size={ButtonSize.SMALL}>
                            {t('tool-panel.operating-point.set-area')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>
            <Infobox
                contentVisible={visibilities.log}
                onContentVisibilityChange={() => visibilityChange('log')}
                title={t('tool-panel.operating-point.log-heading')}
                qa-id="operating-point-infobox">
                <InfoboxContent>
                    {changeInfo && (
                        <React.Fragment>
                            <InfoboxField
                                label={t('tool-panel.operating-point.created')}
                                value={formatDateShort(changeInfo.created)}
                            />
                            <InfoboxField
                                label={t('tool-panel.operating-point.modified')}
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
                        onSave={() => setEditDialogOpen(false)} // TODO
                        onClose={() => setEditDialogOpen(false)}
                    />
                ) : (
                    <InternalOperationalPointEditDialog
                        operationalPoint={operationalPoint}
                        layoutContext={layoutContext}
                        onSave={() => setEditDialogOpen(false)} // TODO
                        onClose={() => setEditDialogOpen(false)}
                    />
                ))}
        </React.Fragment>
    );
};
