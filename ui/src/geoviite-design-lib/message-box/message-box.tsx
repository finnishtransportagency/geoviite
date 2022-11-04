import * as React from 'react';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import styles from './message-box.scss';
import { createClassName } from 'vayla-design-lib/utils';

type MessageBoxProps = {
    children?: React.ReactNode;
    pop?: boolean;
};

export const MessageBox: React.FC<MessageBoxProps> = ({ children, pop }: MessageBoxProps) => {
    const classes = createClassName(
        styles['message-box'],
        pop != undefined && styles['message-box--poppable'],
        pop && styles['message-box--popped'],
    );

    return (
        <div className={classes}>
            <div className="message-box__inner">
                <span className={styles['message-box__icon']}>
                    <Icons.Info color={IconColor.INHERIT} />
                </span>
                <span>{children}</span>
            </div>
        </div>
    );
};
