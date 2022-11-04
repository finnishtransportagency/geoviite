import * as React from 'react';
import styles from './button.scss';
import { IconColor, IconComponent, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';

/**
 * Buttons in Figma:
 * https://www.figma.com/file/Czt3xfeJ32WWwngxy8ypN8/Design-Library?node-id=66%3A933
 */

export enum ButtonVariant {
    PRIMARY = 'button--primary',
    SECONDARY = 'button--secondary',
    WARNING = 'button--warning',
    GHOST = 'button--ghost',
    PRIMARY_WARNING = 'button--primary-warning',
}

export enum ButtonSize {
    SMALL = 'button--size-small',
}

// Pick some properties from default button properties
export type ButtonProps = {
    id?: string;
    variant?: ButtonVariant;
    size?: ButtonSize;
    icon?: IconComponent;
    isProcessing?: boolean;
    children?: React.ReactNode;
    isPressed?: boolean;
    attachLeft?: boolean;
    attachRight?: boolean;
    wide?: boolean;
} & Pick<React.HTMLProps<HTMLButtonElement>, 'disabled' | 'onClick' | 'title'>;

export const Button: React.FC<ButtonProps> = ({
    id,
    variant = ButtonVariant.PRIMARY,
    icon: Icon,
    size,
    isProcessing = false,
    isPressed = false,
    attachLeft,
    attachRight,
    wide,
    ...props
}: ButtonProps) => {
    const className = createClassName(
        styles.button,
        styles[variant],
        Icon && styles['button--has-icon'],
        size && styles[size],
        !props.children && styles['button--no-label'],
        isProcessing && styles['button--has-animation'],
        isPressed && styles['button--pressed'],
        attachLeft && styles['button--attach-left'],
        attachRight && styles['button--attach-right'],
        wide && styles['button--wide'],
    );

    return (
        <button id={id} className={className} {...props}>
            <span className={styles['button__icon-and-animation']}>
                {isProcessing && <div className={styles['button__animation']} />}
                {Icon && (
                    <span className={styles['button__icon']}>
                        <Icon size={IconSize.SMALL} color={IconColor.INHERIT} />
                    </span>
                )}
            </span>
            {props.children && <span className={styles.button__label}>{props.children}</span>}
        </button>
    );
};
