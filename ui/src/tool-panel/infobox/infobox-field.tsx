import React from 'react';
import styles from './infobox.module.scss';
import { createClassName } from 'vayla-design-lib/utils';

type InfoboxFieldProps = {
    label: React.ReactNode;
    qaId?: string;
    value?: React.ReactNode;
    children?: React.ReactNode;
    className?: string;
    hasErrors?: boolean;
};

const InfoboxField: React.FC<InfoboxFieldProps> = ({
    label,
    value,
    children,
    className,
    qaId,
    hasErrors = false,
}: InfoboxFieldProps) => {
    const classes = createClassName(styles['infobox__field'], className);

    return (
        <div className={classes} qa-id={qaId}>
            <div
                className={createClassName(
                    styles['infobox__field-label'],
                    hasErrors && styles['infobox__field-label--error'],
                )}>
                {label}
            </div>
            <div
                className={createClassName(
                    styles['infobox__field-value'],
                    hasErrors && styles['infobox__field-value--error'],
                )}>
                {children || value}
            </div>
        </div>
    );
};

export default InfoboxField;
