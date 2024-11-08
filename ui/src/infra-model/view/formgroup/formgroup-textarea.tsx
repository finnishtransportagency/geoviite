import React from 'react';
import styles from './formgroup.module.scss';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { TextAreaAutoResizing } from 'vayla-design-lib/text-area/text-area-autoresizing';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_GEOMETRY_FILE } from 'user/user-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';

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
                    <PrivilegeRequired privilege={EDIT_GEOMETRY_FILE}>
                        <Button
                            variant={ButtonVariant.GHOST}
                            size={ButtonSize.SMALL}
                            icon={Icons.Edit}
                            onClick={() => props.onEdit && props.onEdit()}
                        />
                    </PrivilegeRequired>
                )}
                {inEditMode && props.onClose && (
                    <Button
                        variant={ButtonVariant.GHOST}
                        size={ButtonSize.SMALL}
                        icon={Icons.Tick}
                        onClick={() => props.onClose && props.onClose()}
                    />
                )}
            </div>
        </div>
    );
};

export default FormgroupTextarea;
