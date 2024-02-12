import React from 'react';
import { FileInput } from 'vayla-design-lib/file-input/file-input';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button } from 'vayla-design-lib/button/button';
import { convertToSerializableFile, SerializableFile } from 'utils/file-utils';
import { useAppNavigate } from 'common/navigate';
import { useTranslation } from 'react-i18next';
import { WriteAccessRequired } from 'user/write-access-required';

export type InframodelLoadButtonProps = {
    onFileSelected: (file: SerializableFile) => void;
};

const InfraModelLoadButton: React.FC<InframodelLoadButtonProps> = ({
    onFileSelected,
}: InframodelLoadButtonProps) => {
    const navigate = useAppNavigate();
    const { t } = useTranslation();
    async function handleFileInputEvent(e: React.ChangeEvent<HTMLInputElement>) {
        const file = e.target.files?.[0];
        if (file) {
            const serializableFile = await convertToSerializableFile(file);
            onFileSelected(serializableFile);
            navigate('inframodel-upload');
        }
    }

    return (
        <React.Fragment>
            <FileInput onChange={(e) => handleFileInputEvent(e)} accept=".xml">
                <WriteAccessRequired>
                    <Button icon={Icons.Append}>{t('im-form.toolbar.upload')}</Button>
                </WriteAccessRequired>
            </FileInput>
        </React.Fragment>
    );
};

export default InfraModelLoadButton;
