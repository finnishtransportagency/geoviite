import * as React from 'react';
import styles from './text-area.scss';
import { IconColor, IconComponent, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';

export type TextAreaProps = {
    Icon?: IconComponent;
    hasError?: boolean;
    wide?: boolean;
    maxlength?: number;
} & React.HTMLProps<HTMLTextAreaElement>;

export const TextArea: React.FC<TextAreaProps> = ({
    Icon,
    hasError = false,
    wide,
    maxlength,
    ...props
}: TextAreaProps) => {
    const [hasFocus, setHasFocus] = React.useState(false);

    const className = createClassName(
        styles['text-area'],
        Icon && styles['text-area--with-icon'],
        props.disabled && styles['text-area--disabled'],
        hasFocus && styles['text-area--focus'],
        hasError && styles['text-area--has-error'],
        wide && styles['text-area--wide'],
    );

    return (
        <div className={className}>
            <div className="text-area__input">
                {Icon && (
                    <div className={styles['text-area__icon']}>
                        <Icon size={IconSize.SMALL} color={IconColor.INHERIT} />
                    </div>
                )}
                <textarea
                    className={styles['text-area__input-element']}
                    {...props}
                    maxLength={maxlength}
                    onFocus={() => setHasFocus(true)}
                    onBlur={() => setHasFocus(false)}
                />
            </div>
        </div>
    );
};
