import * as React from 'react';
import styles from './text-field.scss';
import { IconColor, IconComponent, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { useCloneRef } from 'utils/react-utils';

export enum TextFieldVariant {
    NO_BORDER = 'text-field--no-border',
}

export enum TextInputIconPosition {
    LEFT = 'text-field--with-left-icon',
    RIGHT = 'text-field--with-right-icon',
}

export type TextFieldProps = {
    variant?: TextFieldVariant;
    Icon?: IconComponent;
    iconPosition?: TextInputIconPosition;
    hasError?: boolean;
    wide?: boolean;
    attachLeft?: boolean;
    attachRight?: boolean;
} & React.InputHTMLAttributes<HTMLInputElement>;

export const TextField = React.forwardRef(
    (
        {
            variant,
            Icon,
            iconPosition = TextInputIconPosition.LEFT,
            hasError = false,
            wide,
            attachLeft,
            attachRight,
            ...props
        }: TextFieldProps,
        forwardedRef,
    ) => {
        const localRef = useCloneRef(forwardedRef);

        const hasFocus = document.activeElement == localRef.current;

        const className = createClassName(
            styles['text-field'],
            variant && styles[variant],
            Icon && styles[iconPosition],
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
                        {...props}
                        className={createClassName(
                            styles['text-field__input-element'],
                            props.className,
                        )}
                        ref={(instance) => {
                            localRef.current = instance;
                        }}
                        onFocus={(e) => {
                            props.onFocus && props.onFocus(e);
                        }}
                        onBlur={(e) => {
                            props.onBlur && props.onBlur(e);
                        }}
                    />
                </div>
            </div>
        );
    },
);

TextField.displayName = 'TextField';
