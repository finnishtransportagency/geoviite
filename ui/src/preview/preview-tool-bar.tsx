import * as React from 'react';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { createClassName } from 'vayla-design-lib/utils';
import { LayoutBranch } from 'common/common-model';
import { Radio } from 'vayla-design-lib/radio/radio';
import styles from './preview-view.scss';

export type DesignPublicationMode = 'PUBLISH_CHANGES' | 'MERGE_TO_MAIN';

export type PreviewToolBarParams = {
    onClosePreview: () => void;
    layoutBranch: LayoutBranch;
    designPublicationMode: DesignPublicationMode;
    onChangeDesignPublicationMode: (mode: DesignPublicationMode) => void;
};

export const PreviewToolBar: React.FC<PreviewToolBarParams> = (props: PreviewToolBarParams) => {
    const { t } = useTranslation();

    const showingDesignProject = props.layoutBranch !== 'MAIN';
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
                            checked={props.designPublicationMode === 'PUBLISH_CHANGES'}
                            onChange={() => props.onChangeDesignPublicationMode('PUBLISH_CHANGES')}>
                            {t('preview-toolbar.publish-changes')}
                        </Radio>
                        <Radio
                            checked={props.designPublicationMode === 'MERGE_TO_MAIN'}
                            onChange={() => props.onChangeDesignPublicationMode('MERGE_TO_MAIN')}>
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
