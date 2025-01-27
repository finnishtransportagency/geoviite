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
    X_SMALL = 'button--size-x-small',
    BY_CONTENT = 'button--size-by-content',
}

export enum ButtonIconPosition {
    START,
    END,
}

// Pick some properties from default button properties
export type ButtonProps = {
    id?: string;
    variant?: ButtonVariant;
    size?: ButtonSize;
    icon?: IconComponent;
    iconPosition?: ButtonIconPosition;
    isProcessing?: boolean;
    children?: React.ReactNode;
    isPressed?: boolean;
    attachLeft?: boolean;
    attachRight?: boolean;
    wide?: boolean;
    className?: string;
    inheritTypography?: boolean;
} & Pick<React.HTMLProps<HTMLButtonElement>, 'disabled' | 'onClick' | 'title'>;

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(function Button(
    {
        id,
        variant = ButtonVariant.PRIMARY,
        icon: Icon,
        iconPosition = ButtonIconPosition.START,
        size,
        isProcessing = false,
        isPressed = false,
        attachLeft,
        attachRight,
        wide,
        className,
        inheritTypography = false,
        ...props
    }: ButtonProps,
    ref,
) {
    const classes = createClassName(
        styles.button,
        styles[variant],
        className,
        Icon && styles['button--has-icon'],
        iconPosition === ButtonIconPosition.END && styles['button--icon-at-end'],
        size && styles[size],
        !props.children && styles['button--no-label'],
        isProcessing && styles['button--has-animation'],
        isPressed && styles['button--pressed'],
        attachLeft && styles['button--attach-left'],
        attachRight && styles['button--attach-right'],
        wide && styles['button--wide'],
        inheritTypography && styles['button--inherit-typography'],
    );

    return (
        <button id={id} className={classes} ref={ref} {...props}>
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
});
