import * as React from 'react';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import styles from './message-box.scss';
import { createClassName } from 'vayla-design-lib/utils';

export enum MessageBoxType {
    GHOST = 'GHOST',
    INFO = 'INFO',
    ERROR = 'ERROR',
}

type MessageBoxProps = {
    children?: React.ReactNode;
    pop?: boolean;
    type?: MessageBoxType;
    qaId?: string;
};

const styleByType: Record<MessageBoxType, string | undefined> = {
    [MessageBoxType.GHOST]: undefined,
    [MessageBoxType.INFO]: styles['message-box--info'],
    [MessageBoxType.ERROR]: styles['message-box--error'],
};

const iconByType: Record<MessageBoxType, React.ReactNode> = {
    [MessageBoxType.GHOST]: <Icons.Info color={IconColor.INHERIT} />,
    [MessageBoxType.INFO]: <Icons.Info color={IconColor.INHERIT} />,
    [MessageBoxType.ERROR]: <Icons.StatusError color={IconColor.INHERIT} />,
};

export const MessageBox: React.FC<MessageBoxProps> = ({
    children,
    pop,
    type = MessageBoxType.INFO,
}: MessageBoxProps) => {
    const classes = createClassName(
        styles['message-box'],
        pop !== undefined && styles['message-box--poppable'],
        pop && styles['message-box--popped'],
        styleByType[type],
    );

    const iconClasses = createClassName(
        styles['message-box__icon'],
        type === MessageBoxType.ERROR && styles['message-box__icon--error'],
    );
    return (
        <div className={classes}>
            <div className="message-box__inner">
                <span className={iconClasses}>{iconByType[type]}</span>
                <span>{children}</span>
            </div>
        </div>
    );
};
