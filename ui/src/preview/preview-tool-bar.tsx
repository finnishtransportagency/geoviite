import * as React from 'react';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { createClassName } from 'vayla-design-lib/utils';
import { LayoutDesignId } from 'common/common-model';
import { Radio } from 'vayla-design-lib/radio/radio';
import styles from './preview-view.scss';

type DesignPublicationMode = 'PUBLISH_CHANGES' | 'DESIGN_TO_DRAFT';

export type PreviewToolBarParams = {
    onClosePreview: () => void;
    designId: LayoutDesignId | undefined;
};

export const PreviewToolBar: React.FC<PreviewToolBarParams> = (props: PreviewToolBarParams) => {
    const { t } = useTranslation();
    const [designPublicationMode, setDesignPublicationMode] =
        React.useState<DesignPublicationMode>('PUBLISH_CHANGES');

    const showingDesignProject = !!props.designId;
    const className = createClassName(
        'preview-tool-bar',
        showingDesignProject ? 'preview-tool-bar__design' : 'preview-tool-bar__draft',
    );

    return (
        <div className={className}>
            <div className={'preview-tool-bar__title'}>
                {showingDesignProject
                    ? t('preview-toolbar.title-design')
                    : t('preview-toolbar.title-draft')}
                {showingDesignProject && (
                    <span className={styles['preview-tool-bar__radio-buttons']}>
                        <Radio
                            checked={designPublicationMode === 'PUBLISH_CHANGES'}
                            onChange={() => setDesignPublicationMode('PUBLISH_CHANGES')}>
                            {t('preview-toolbar.publish-changes')}
                        </Radio>
                        <Radio
                            checked={designPublicationMode === 'DESIGN_TO_DRAFT'}
                            onChange={() => setDesignPublicationMode('DESIGN_TO_DRAFT')}>
                            {t('preview-toolbar.design-to-draft')}
                        </Radio>
                    </span>
                )}
            </div>

            <div className={'preview-tool-bar__right-section'}>
                <Button
                    variant={ButtonVariant.PRIMARY}
                    qa-id="go-to-track-layout-view"
                    onClick={props.onClosePreview}>
                    {showingDesignProject
                        ? t('preview-toolbar.return-to-design')
                        : t('preview-toolbar.return-to-draft')}
                </Button>
            </div>
        </div>
    );
};
