import React from 'react';
import styles from './infobox.module.scss';
import { createClassName } from 'vayla-design-lib/utils';

type InfoboxTextProps = {
    value?: string;
    className?: string;
};

const InfoboxText: React.FC<InfoboxTextProps> = ({ value, className }: InfoboxTextProps) => {
    return <div className={createClassName(styles['infobox__text'], className)}>{value}</div>;
};

export default InfoboxText;
