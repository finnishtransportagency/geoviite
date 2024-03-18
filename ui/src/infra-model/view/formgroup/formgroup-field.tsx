import React from 'react';
import styles from './formgroup.module.scss';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { PrivilegeRequired } from 'user/privilege-required';
import { PRIV_EDIT_GEOMETRY_FILE } from 'user/user-model';

type InfoboxFieldProps = {
    label: string;
    qaId?: string;
    value?: React.ReactNode;
    children?: React.ReactNode;
    inEditMode?: boolean;
    onEdit?: () => void;
    onClose?: () => void;
};

const FormgroupField: React.FC<InfoboxFieldProps> = ({
    label,
    value,
    children,
    inEditMode = false,
    qaId,
    ...props
}: InfoboxFieldProps) => {
    return (
        <div className={styles['formgroup__field']} qa-id={qaId}>
            <div className={styles['formgroup__field-label']}>{label}</div>
            <div className={styles['formgroup__field-value']}>{children || value}</div>
            <div className={styles['formgroup__edit-icon']}>
                {!inEditMode && props.onEdit && (
                    <PrivilegeRequired privilege={PRIV_EDIT_GEOMETRY_FILE}>
                        <div onClick={() => props.onEdit && props.onEdit()}>
                            <Icons.Edit size={IconSize.SMALL} />
                        </div>
                    </PrivilegeRequired>
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

export default FormgroupField;
