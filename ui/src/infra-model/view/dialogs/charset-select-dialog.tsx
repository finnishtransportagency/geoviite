import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';
import { XmlCharset } from 'infra-model/infra-model-slice';
import React from 'react';
import styles from '../form/infra-model-form.module.scss';
import { useTranslation } from 'react-i18next';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Dialog } from 'vayla-design-lib/dialog/dialog';
import { Dropdown, Item } from 'vayla-design-lib/dropdown/dropdown';

const xmlEncodingOptions: Item<XmlCharset>[] = [
    { name: 'ISO-8859-1', value: 'ISO_8859_1' },
    { name: 'UTF-8', value: 'UTF_8' },
    { name: 'UTF-16', value: 'UTF_16' },
    { name: 'US ASCII', value: 'US_ASCII' },
];

export type CharsetSelectDialogProps = {
    title: string;
    value: XmlCharset | undefined;
    onSelect: (charset: XmlCharset | undefined) => void;
    onCancel: () => void;
    children?: React.ReactNode;
};

export const CharsetSelectDialog: React.FC<CharsetSelectDialogProps> = ({
    title,
    children,
    value,
    onSelect,
    onCancel,
}: CharsetSelectDialogProps) => {
    const { t } = useTranslation();
    const [overrideCharset, setOverrideCharset] = React.useState<XmlCharset>();
    return (
        <Dialog
            title={title}
            onClose={onCancel}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button variant={ButtonVariant.SECONDARY} onClick={onCancel}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        onClick={() => {
                            onSelect(overrideCharset);
                        }}>
                        {t('im-form.file-handling-failed.try-again')}
                    </Button>
                </div>
            }>
            {children}
            <div className={styles['infra-model-upload-failed__encode-container']}>
                <label className={styles['infra-model-upload-failed__checkbox-label']}>
                    {t('im-form.file-handling-failed.encoding')}
                </label>
                <Dropdown
                    options={xmlEncodingOptions}
                    value={overrideCharset || value}
                    onChange={(newEncoding: XmlCharset) => setOverrideCharset(newEncoding)}
                />
            </div>
        </Dialog>
    );
};
