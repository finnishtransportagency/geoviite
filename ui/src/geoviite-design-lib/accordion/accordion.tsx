import * as React from 'react';
import styles from './accordion.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { AccordionToggle } from 'vayla-design-lib/accordion-toggle/accordion-toggle';
import { Eye } from 'geoviite-design-lib/eye/eye';

type AccordionProps = {
    open: boolean;
    header: string;
    subheader?: string;
    onToggle: React.MouseEventHandler;
    children: React.ReactNode;
    visibility?: boolean;
    onVisibilityToggle?: React.MouseEventHandler;
    onHeaderClick?: () => void;
    headerSelected?: boolean;
    fetchingContent?: boolean;
    disabled?: boolean;
    eyeHidden?: boolean;
    qaId?: string;
    className?: string;
};

export const Accordion: React.FC<AccordionProps> = ({
    header,
    subheader,
    open,
    onToggle,
    children,
    onVisibilityToggle,
    visibility,
    onHeaderClick,
    headerSelected = false,
    fetchingContent = false,
    disabled,
    qaId,
    eyeHidden = false,
    className,
}: AccordionProps) => {
    const accordionClasses = createClassName(
        styles['accordion__header'],
        headerSelected && styles['accordion__header--selected'],
    );

    const titleClasses = createClassName(
        styles['accordion__header-title'],
        onHeaderClick && styles['accordion__header-title--clickable'],
    );

    const classes = createClassName('accordion', className);

    return (
        <div className={classes} qa-id={qaId}>
            <h4 className={accordionClasses}>
                <AccordionToggle
                    onToggle={disabled ? undefined : onToggle}
                    open={open}
                    disabled={disabled}
                />
                <span
                    className={titleClasses}
                    title={header}
                    onClick={() => onHeaderClick && onHeaderClick()}>
                    {header}
                    {subheader && <div className="accordion__subheader">{subheader}</div>}
                </span>
                {onVisibilityToggle && !eyeHidden && (
                    <Eye
                        onVisibilityToggle={onVisibilityToggle}
                        visibility={visibility}
                        fetchingContent={fetchingContent}
                    />
                )}
            </h4>
            {open && <div className={styles['accordion__body']}>{children}</div>}
        </div>
    );
};
