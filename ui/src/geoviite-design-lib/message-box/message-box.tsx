import * as React from 'react';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import styles from './message-box.scss';
import { createClassName } from 'vayla-design-lib/utils';

type MessageBoxType = 'INFO' | 'ERROR';

type MessageBoxProps = {
    children?: React.ReactNode;
    pop?: boolean;
    type?: MessageBoxType;
    qaId?: string;
};

export const MessageBox: React.FC<MessageBoxProps> = ({
    children,
    pop,
    type = 'INFO',
}: MessageBoxProps) => {
    const showingError = type === 'ERROR';

    const classes = createClassName(
        styles['message-box'],
        pop !== undefined && styles['message-box--poppable'],
        pop && styles['message-box--popped'],
        showingError && styles['message-box--error'],
    );

    const iconClasses = createClassName(
        styles['message-box__icon'],
        showingError && styles['message-box__icon--error'],
    );
    return (
        <div className={classes}>
            <div className="message-box__inner">
                <span className={iconClasses}>
                    {showingError ? (
                        <Icons.StatusError color={IconColor.INHERIT} />
                    ) : (
                        <Icons.Info color={IconColor.INHERIT} />
                    )}
                </span>
                <span>{children}</span>
            </div>
        </div>
    );
};
