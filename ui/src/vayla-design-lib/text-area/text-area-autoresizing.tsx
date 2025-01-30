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

export const TextAreaAutoResizing: React.FC<TextAreaProps> = ({
    Icon,
    hasError = false,
    wide,
    maxlength,
    onChange,
    ...props
}: TextAreaProps) => {
    const inputRef = React.useRef<HTMLTextAreaElement>(null);
    const [hasFocus, setHasFocus] = React.useState(false);

    // Set initial "hasFocus"
    React.useEffect(() => {
        setHasFocus(document.activeElement === inputRef.current);
    });

    const className = createClassName(
        styles['text-area'],
        Icon && styles['text-area--with-icon'],
        props.disabled && styles['text-area--disabled'],
        hasFocus && styles['text-area--focus'],
        hasError && styles['text-area--has-error'],
        wide && styles['text-area--wide'],
    );

    React.useEffect(() => {
        const textarea = inputRef.current;
        if (textarea) {
            textarea.style.height = 'inherit';
            textarea.style.height = `${textarea.scrollHeight}px`;
        }
    }, []);

    const updateHeight = (e: React.FormEvent<HTMLTextAreaElement>) => {
        const textarea = e.currentTarget;
        textarea.style.height = 'inherit';
        textarea.style.height = `${textarea.scrollHeight}px`;
    };

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
                    ref={inputRef}
                    maxLength={maxlength}
                    onChange={(e) => {
                        if (onChange) onChange(e);
                        updateHeight(e);
                    }}
                    onFocus={() => setHasFocus(true)}
                    onBlur={() => setHasFocus(false)}
                />
            </div>
        </div>
    );
};
