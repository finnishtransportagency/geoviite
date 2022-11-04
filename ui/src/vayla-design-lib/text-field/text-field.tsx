import * as React from 'react';
import styles from './text-field.scss';
import { IconColor, IconComponent, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';

export enum TextFieldVariant {
    NO_BORDER = 'text-field--no-border',
}

export type TextFieldProps = {
    variant?: TextFieldVariant;
    Icon?: IconComponent;
    hasError?: boolean;
    wide?: boolean;
    attachLeft?: boolean;
    attachRight?: boolean;
    inputRef?: React.MutableRefObject<HTMLInputElement | null>;
} & React.InputHTMLAttributes<HTMLInputElement>;

export const TextField: React.FC<TextFieldProps> = ({
    variant,
    Icon,
    hasError = false,
    wide,
    attachLeft,
    attachRight,
    inputRef,
    ...props
}: TextFieldProps) => {
    const inputRefInternal = inputRef || React.useRef<HTMLInputElement>(null);
    const [hasFocus, setHasFocus] = React.useState(false);

    //  Set initial "hasFocus"
    React.useEffect(() => {
        setHasFocus(document.activeElement == inputRefInternal.current);
    });

    const className = createClassName(
        styles['text-field'],
        variant && styles[variant],
        Icon && styles['text-field--with-icon'],
        props.disabled && styles['text-field--disabled'],
        hasFocus && styles['text-field--focus'],
        hasError && styles['text-field--has-error'],
        wide && styles['text-field--wide'],
        attachLeft && styles['text-field--attach-left'],
        attachRight && styles['text-field--attach-right'],
    );

    return (
        <div className={className}>
            <div className={styles['text-field__input']}>
                {Icon && (
                    <div className={styles['text-field__icon']}>
                        <Icon size={IconSize.SMALL} color={IconColor.INHERIT} />
                    </div>
                )}
                <input
                    className={styles['text-field__input-element']}
                    {...props}
                    ref={inputRefInternal}
                    onFocus={(e) => {
                        setHasFocus(true);
                        props.onFocus && props.onFocus(e);
                    }}
                    onBlur={(e) => {
                        setHasFocus(false);
                        props.onBlur && props.onBlur(e);
                    }}
                />
            </div>
        </div>
    );
};
