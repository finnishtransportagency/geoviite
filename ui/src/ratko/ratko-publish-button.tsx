import * as React from 'react';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { pushToRatko } from 'ratko/ratko-api';
import { useTranslation } from 'react-i18next';
import { Dialog } from 'vayla-design-lib/dialog/dialog';

type RatkoPublishButtonProps = {
    size?: ButtonSize;
};

const RatkoPublishButton: React.FC<RatkoPublishButtonProps> = ({ size }) => {
    const { t } = useTranslation();
    const [isPublishing, setIsPublishing] = React.useState(false);
    const [showingConfirmation, setShowingConfirmation] = React.useState(false);
    const publishToRatko = () => {
        setShowingConfirmation(false);
        setIsPublishing(true);
        // TODO Catch cases where RatkoAPI is not online
        pushToRatko().finally(() => setIsPublishing(false));
    };

    return (
        <React.Fragment>
            <Button
                onClick={() => setShowingConfirmation(true)}
                disabled={isPublishing}
                isProcessing={isPublishing}
                variant={ButtonVariant.PRIMARY}
                size={size}
                icon={Icons.Redo}>
                {t('publishing.publish-to-ratko')}
            </Button>
            {showingConfirmation && (
                <Dialog
                    title={t('publishing.publish-to-ratko')}
                    allowClose={false}
                    style={{ minWidth: 300 }}
                    footerContent={
                        <React.Fragment>
                            <Button
                                onClick={() => setShowingConfirmation(false)}
                                variant={ButtonVariant.SECONDARY}>
                                {t('button.cancel')}
                            </Button>
                            <Button onClick={publishToRatko} variant={ButtonVariant.PRIMARY}>
                                {t('button.ok')}
                            </Button>
                        </React.Fragment>
                    }>
                    {t('publishing.publish-confirmation')}
                </Dialog>
            )}
        </React.Fragment>
    );
};

export default RatkoPublishButton;
