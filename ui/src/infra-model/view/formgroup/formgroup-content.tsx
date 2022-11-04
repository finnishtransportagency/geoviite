import React from 'react';
import styles from './formgroup.module.scss';

type InfoboxContentProps = {
    title: string;
    children: React.ReactNode;
};

const FormgroupContent: React.FC<InfoboxContentProps> = ({
    title,
    children,
}: InfoboxContentProps) => {
    return (
        <div className={styles['formgroup__content']}>
            <div className={styles['formgroup__title']}>{title}</div>
            {children}
        </div>
    );
};

export default FormgroupContent;
