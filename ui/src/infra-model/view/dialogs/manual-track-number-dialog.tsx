import { TrackNumber } from 'common/common-model';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import * as React from 'react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { brand } from 'common/brand';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';

type ManualTrackNumberDialogProps = {
    nameSuggestion: string | undefined;
    onSave: (trackNumber: TrackNumber) => void;
    onClose: () => void;
};

export const ManualTrackNumberDialog: React.FC<ManualTrackNumberDialogProps> = ({
    nameSuggestion,
    onSave,
    onClose,
}) => {
    const { t } = useTranslation();
    const [trackNumber, setTrackNumber] = useState(nameSuggestion ?? '');
    const nameFieldRef = React.useRef<HTMLInputElement>(null);

    React.useEffect(() => nameFieldRef?.current?.focus(), []);

    const saveEnabled = trackNumber.length > 1;
    const onClickSave = () => {
        Snackbar.success('im-form.track-number-manually-set');
        onSave(brand(trackNumber));
    };

    return (
        <Dialog
            title={t('im-form.manual-track-number-dialog-title')}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button variant={ButtonVariant.SECONDARY} icon={Icons.Delete} onClick={onClose}>
                        {t('im-form.dialog-cancel-button')}
                    </Button>
                    <Button disabled={!saveEnabled} onClick={onClickSave}>
                        {t('im-form.dialog-set-manually-button')}
                    </Button>
                </div>
            }>
            <FormLayoutColumn>
                <FieldLayout
                    label={t('im-form.manual-track-number-dialog-field-title')}
                    value={
                        <TextField
                            value={trackNumber}
                            maxLength={100}
                            onChange={(e) => setTrackNumber(e.target.value)}
                            wide
                            ref={nameFieldRef}
                        />
                    }
                />
            </FormLayoutColumn>
        </Dialog>
    );
};
