import React from 'react';
import { FileInput } from 'vayla-design-lib/file-input/file-input';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button } from 'vayla-design-lib/button/button';
import { convertToSerializableFile, SerializableFile } from 'utils/file-utils';
import { useAppNavigate } from 'common/navigate';
import { useCommonDataAppSelector } from 'store/hooks';
import { useTranslation } from 'react-i18next';

export type InframodelLoadButtonProps = {
    onFileSelected: (file: SerializableFile) => void;
};

const InfraModelLoadButton: React.FC<InframodelLoadButtonProps> = ({
    onFileSelected,
}: InframodelLoadButtonProps) => {
    const navigate = useAppNavigate();
    const { t } = useTranslation();
    const userHasWriteRole = useCommonDataAppSelector((state) => state.userHasWriteRole);
    async function handleFileInputEvent(e: React.ChangeEvent<HTMLInputElement>) {
        if (e.target.files) {
            const serializableFile = await convertToSerializableFile(e.target.files[0]);
            onFileSelected(serializableFile);
            navigate('inframodel-upload');
        }
    }

    return (
        <React.Fragment>
            <FileInput onChange={(e) => handleFileInputEvent(e)} accept=".xml">
                {userHasWriteRole && (
                    <Button icon={Icons.Append}>{t('im-form.toolbar.upload')}</Button>
                )}
            </FileInput>
        </React.Fragment>
    );
};

export default InfraModelLoadButton;
