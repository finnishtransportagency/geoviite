import React from 'react';
import styles from './formgroup.module.scss';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_GEOMETRY_FILE } from 'user/user-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';

type InfoboxFieldProps = {
    label: string;
    qaId?: string;
    value?: React.ReactNode;
    children?: React.ReactNode;
    inEditMode?: boolean;
    onEdit?: () => void;
    onClose?: () => void;
    disabled?: boolean;
};

const FormgroupField: React.FC<InfoboxFieldProps> = ({
    label,
    value,
    children,
    inEditMode = false,
    qaId,
    disabled = false,
    ...props
}: InfoboxFieldProps) => {
    return (
        <div className={styles['formgroup__field']} qa-id={qaId}>
            <div className={styles['formgroup__field-label']}>{label}</div>
            <div className={styles['formgroup__field-value']}>{children || value}</div>
            <div className={styles['formgroup__edit-icon']}>
                {!inEditMode && props.onEdit && (
                    <PrivilegeRequired privilege={EDIT_GEOMETRY_FILE}>
                        <Button
                            variant={ButtonVariant.GHOST}
                            icon={Icons.Edit}
                            disabled={disabled}
                            size={ButtonSize.SMALL}
                            onClick={() => props.onEdit && props.onEdit()}
                        />
                    </PrivilegeRequired>
                )}
                {inEditMode && props.onClose && (
                    <Button
                        variant={ButtonVariant.GHOST}
                        icon={Icons.Tick}
                        size={ButtonSize.SMALL}
                        onClick={() => props.onClose && props.onClose()}
                    />
                )}
            </div>
        </div>
    );
};

export default FormgroupField;
