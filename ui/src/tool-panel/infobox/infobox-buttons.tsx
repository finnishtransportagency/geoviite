import React from 'react';
import styles from './infobox.module.scss';
import { createClassName } from 'vayla-design-lib/utils';

type InfoboxButtonsProps = {
    verticalLayout?: boolean;
    extraClassName?: string;
    children: React.ReactNode;
};

const InfoboxButtons: React.FC<InfoboxButtonsProps> = ({
    verticalLayout,
    extraClassName,
    children,
}: InfoboxButtonsProps) => {
    const className = createClassName(
        styles['infobox__buttons'],
        verticalLayout && styles['infobox__buttons--vertical'],
        extraClassName,
    );
    return <div className={className}>{children}</div>;
};

export default InfoboxButtons;
