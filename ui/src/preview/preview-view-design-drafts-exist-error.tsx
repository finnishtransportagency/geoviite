import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import { Trans, useTranslation } from 'react-i18next';
import * as React from 'react';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';

export type DesignDraftsExistErrorProps = { goToPublishChangesMode: () => void };

export const DesignDraftsExistError: React.FC<DesignDraftsExistErrorProps> = ({
    goToPublishChangesMode,
}) => {
    const { t } = useTranslation();
    const actionLink = (
        <AnchorLink
            onClick={(e) => {
                goToPublishChangesMode();
                e.preventDefault();
            }}
        />
    );
    return (
        <div>
            <MessageBox type="ERROR">
                <Trans
                    t={t}
                    i18nKey={'preview-view.design-has-design-drafts-error'}
                    components={{ actionLink }}
                />
            </MessageBox>
        </div>
    );
};
