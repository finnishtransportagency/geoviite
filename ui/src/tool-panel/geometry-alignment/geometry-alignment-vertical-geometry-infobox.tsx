import React from 'react';
import { useTranslation } from 'react-i18next';
import Infobox from 'tool-panel/infobox/infobox';
import { Switch } from 'vayla-design-lib/switch/switch';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import InfoboxContent from 'tool-panel/infobox/infobox-content';

type GeometryAlignmentVerticalGeometryInfoboxProps = {
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
    onVerticalGeometryDiagramVisibilityChange: (visibility: boolean) => void;
    verticalGeometryDiagramVisible: boolean;
};

export const GeometryAlignmentVerticalGeometryInfobox: React.FC<
    GeometryAlignmentVerticalGeometryInfoboxProps
> = ({
    contentVisible,
    onContentVisibilityChange,
    verticalGeometryDiagramVisible,
    onVerticalGeometryDiagramVisibilityChange,
}) => {
    const { t } = useTranslation();

    return (
        <Infobox
            title={t('tool-panel.alignment.vertical-geometry.heading')}
            contentVisible={contentVisible}
            onContentVisibilityChange={onContentVisibilityChange}>
            <InfoboxContent>
                <InfoboxField
                    label={t('tool-panel.alignment.vertical-geometry.diagram-visibility')}
                    value={
                        <Switch
                            checked={verticalGeometryDiagramVisible}
                            onCheckedChange={onVerticalGeometryDiagramVisibilityChange}
                        />
                    }
                />
            </InfoboxContent>
        </Infobox>
    );
};
