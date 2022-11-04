import * as React from 'react';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';

export type PreviewToolBarParams = {
    onClosePreview: () => void;
};

export const PreviewToolBar: React.FC<PreviewToolBarParams> = (props: PreviewToolBarParams) => {
    const { t } = useTranslation();

    return (
        <div className={`preview-tool-bar`}>
            <div className={'preview-tool-bar__title'}>{t('preview-toolbar.title')}</div>

            <div className={'preview-tool-bar__right-section'}>
                <Button variant={ButtonVariant.SECONDARY} onClick={props.onClosePreview}>
                    {t('preview-toolbar.return-to-draft')}
                </Button>
            </div>
        </div>
    );
};
