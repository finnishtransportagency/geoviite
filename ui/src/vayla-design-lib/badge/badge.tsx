import * as React from 'react';
import styles from './checkbox.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';

export type BadgeProps = {
    foo?: string;
} & React.InputHTMLAttributes<HTMLInputElement>;

export const Badge: React.FC<BadgeProps> = ({ children, ...props }: BadgeProps) => {
    return <div>Badge</div>;
};
