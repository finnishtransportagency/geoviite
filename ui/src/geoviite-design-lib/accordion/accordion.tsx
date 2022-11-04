import * as React from 'react';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import styles from './accordion.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { AccordionToggle } from 'vayla-design-lib/accordion-toggle/accordion-toggle';
import CircularProgress from 'vayla-design-lib/progress/circular-progress';

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
}: AccordionProps) => {
    const accordionClasses = createClassName(
        styles['accordion__header'],
        headerSelected && styles['accordion__header--selected'],
    );

    const titleClasses = createClassName(
        styles['accordion__header-title'],
        onHeaderClick && styles['accordion__header-title--clickable'],
    );

    return (
        <div className="accordion">
            <h4 className={accordionClasses}>
                <AccordionToggle onToggle={disabled ? undefined : onToggle} open={open} disabled={disabled} />
                <span
                    className={titleClasses}
                    title={header}
                    onClick={() => onHeaderClick && onHeaderClick()}>
                    {header}
                    {subheader && <div className="accordion__subheader">{subheader}</div>}
                </span>
                {onVisibilityToggle && (
                    <span
                        className={`${styles['accordion__visibility']} ${
                            visibility ? styles['accordion__visibility--visible'] : ''
                        }`}
                        onClick={onVisibilityToggle}>
                        {fetchingContent ? (
                            <CircularProgress />
                        ) : (
                            <Icons.Eye color={IconColor.INHERIT} />
                        )}
                    </span>
                )}
            </h4>
            {open && <div className={styles['accordion__body']}>{children}</div>}
        </div>
    );
};
