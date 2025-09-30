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
import { ExternalOperatingPointEditDialog } from 'tool-panel/operating-point/external-operating-point-edit-dialog';
import { InternalOperatingPointEditDialog } from 'tool-panel/operating-point/internal-operating-point-edit-dialog';

type OperatingPointInfoboxProps = {
    layoutContext: LayoutContext;
    visibilities: OperatingPointInfoboxVisibilities;
    onVisibilityChange: (visibilities: OperatingPointInfoboxVisibilities) => void;
};

export const OperatingPointInfobox: React.FC<OperatingPointInfoboxProps> = ({
    visibilities,
    onVisibilityChange,
    layoutContext,
}) => {
    const { t } = useTranslation();
    const visibilityChange = (key: keyof OperatingPointInfoboxVisibilities) => {
        onVisibilityChange({ ...visibilities, [key]: !visibilities[key] });
    };

    const [editDialogOpen, setEditDialogOpen] = React.useState(true);
    const isExternal = true;

    return (
        <React.Fragment>
            <Infobox
                contentVisible={visibilities.basic}
                onContentVisibilityChange={() => visibilityChange('basic')}
                title={t('tool-panel.operating-point.basic-info-heading')}
                qa-id="operating-point-infobox"
                onEdit={() => setEditDialogOpen(true)}
                iconDisabled={layoutContext.publicationState === 'OFFICIAL'}
                disabledReason={''}>
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.operating-point.identifier')}
                        value={<OperatingPointOid />}
                    />
                    <InfoboxField label={t('tool-panel.operating-point.name')} value={'Nimi lol'} />
                    <InfoboxField
                        label={t('tool-panel.operating-point.state')}
                        value={'Käytössä lol'}
                    />
                    <InfoboxField
                        label={t('tool-panel.operating-point.type-raide')}
                        value={'AAAAAA'}
                    />
                    <InfoboxField
                        label={t('tool-panel.operating-point.type-rinf')}
                        value={'AAAAAAA'}
                    />
                    <InfoboxField label={t('tool-panel.operating-point.uic-code')} value={'1337'} />
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
                        value={'1337 E, 666 N'}
                    />
                    <InfoboxButtons>
                        <Button variant={ButtonVariant.SECONDARY} size={ButtonSize.SMALL}>
                            {t('tool-panel.operating-point.focus-on-map')}
                        </Button>
                    </InfoboxButtons>
                    <InfoboxButtons>
                        <Button variant={ButtonVariant.SECONDARY} size={ButtonSize.SMALL}>
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
                    <InfoboxField
                        label={t('tool-panel.operating-point.created')}
                        value={'19.11.2022'}
                    />
                    <InfoboxField
                        label={t('tool-panel.operating-point.modified')}
                        value={'24.12.2024'}
                    />
                    <InfoboxButtons>
                        <Button variant={ButtonVariant.SECONDARY} size={ButtonSize.SMALL}>
                            {t('tool-panel.show-in-publication-log')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>
            {editDialogOpen &&
                (isExternal ? (
                    <ExternalOperatingPointEditDialog
                        onSave={() => setEditDialogOpen(false)}
                        onClose={() => setEditDialogOpen(false)}
                    />
                ) : (
                    <InternalOperatingPointEditDialog
                        onSave={() => setEditDialogOpen(false)}
                        onClose={() => setEditDialogOpen(false)}
                    />
                ))}
        </React.Fragment>
    );
};
