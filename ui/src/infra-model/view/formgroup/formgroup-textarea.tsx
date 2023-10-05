import React from 'react';
import styles from './formgroup.module.scss';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { WriteAccessRequired } from 'user/write-access-required';
import { TextAreaAutoResizing } from 'vayla-design-lib/text-area/text-area-autoresizing';

type TextareaProps = {
    label: string;
    defaultDisplayedValue: string;
    value?: string;
    inEditMode?: boolean;
    onEdit?: () => void;
    onChange?: (e: React.FormEvent<HTMLTextAreaElement>) => void;
    onClose?: () => void;
};

const FormgroupTextarea: React.FC<TextareaProps> = ({
    label,
    value,
    defaultDisplayedValue,
    inEditMode = false,
    ...props
}: TextareaProps) => {
    return (
        <div className={styles['formgroup__textarea']}>
            <div className={styles['formgroup__textarea-content']}>
                {inEditMode && props.onClose && (
                    <TextAreaAutoResizing
                        label={label}
                        value={value}
                        wide
                        readOnly={false}
                        maxlength={250}
                        onChange={(e) => props.onChange && props.onChange(e)}
                    />
                )}
                {!inEditMode && props.onEdit && (
                    <p className={styles['formgroup__paragraph-with-linebreaks']}>
                        {value ? value : defaultDisplayedValue}
                    </p>
                )}
            </div>

            <div className={styles['formgroup__edit-icon']}>
                {!inEditMode && props.onEdit && (
                    <WriteAccessRequired>
                        <div onClick={() => props.onEdit && props.onEdit()}>
                            <Icons.Edit size={IconSize.SMALL} />
                        </div>
                    </WriteAccessRequired>
                )}
                {inEditMode && props.onClose && (
                    <div onClick={() => props.onClose && props.onClose()}>
                        <Icons.Tick size={IconSize.SMALL} />
                    </div>
                )}
            </div>
        </div>
    );
};

export default FormgroupTextarea;
