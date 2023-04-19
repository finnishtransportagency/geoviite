import React from 'react';
import styles from './formgroup.module.scss';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useCommonDataAppSelector } from 'store/hooks';

type InfoboxFieldProps = {
    label: string;
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
    ...props
}: InfoboxFieldProps) => {
    const userHasWriteRole = useCommonDataAppSelector((state) => state.userHasWriteRole);
    return (
        <div className={styles['formgroup__field']}>
            <div className={styles['formgroup__field-label']}>{label}</div>
            <div className={styles['formgroup__field-value']}>{children || value}</div>
            <div className={styles['formgroup__edit-icon']}>
                {!inEditMode && props.onEdit && userHasWriteRole && (
                    <div onClick={() => props.onEdit && props.onEdit()}>
                        <Icons.Edit size={IconSize.SMALL} />
                    </div>
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
