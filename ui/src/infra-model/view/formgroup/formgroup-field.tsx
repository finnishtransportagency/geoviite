import React from 'react';
import styles from './formgroup.module.scss';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_GEOMETRY_FILE } from 'user/user-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { createClassName } from 'vayla-design-lib/utils';

type InfoboxFieldProps = {
    label: string;
    qaId?: string;
    value?: React.ReactNode;
    children?: React.ReactNode;
    inEditMode?: boolean;
    onEdit?: () => void;
    onClose?: () => void;
    disabled?: boolean;
    errors?: string[];
};

const FormgroupField: React.FC<InfoboxFieldProps> = ({
    label,
    value,
    children,
    inEditMode = false,
    qaId,
    disabled = false,
    errors = [],
    ...props
}: InfoboxFieldProps) => {
    const className = createClassName(
        styles['formgroup__field'],
        inEditMode && styles['formgroup__field--edit-mode'],
        errors?.length > 0 && styles['formgroup__field--has-errors'],
        (children || value) && styles['formgroup__field--has-value-content'],
    );
    return (
        <div className={className} qa-id={qaId}>
            <div className={styles['formgroup__field-label']}>{label}</div>
            <div className={styles['formgroup__field-value']}>
                <div className={styles['formgroup__field-value-content']}>{children || value}</div>
                {errors && errors.length > 0 && (
                    <div className={styles['formgroup__field-errors']}>
                        {errors.map((error) => (
                            <div key={error} className={styles['formgroup__field-error']}>
                                {error}
                            </div>
                        ))}
                    </div>
                )}
            </div>
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
