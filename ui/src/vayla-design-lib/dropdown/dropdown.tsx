import * as React from 'react';
import styles from './dropdown.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { IconColor, IconComponent, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { CloseableModal } from 'vayla-design-lib/closeable-modal/closeable-modal';
import { useImmediateLoader } from 'utils/react-utils';
import { first } from 'utils/array-utils';
import { useTranslation } from 'react-i18next';
import { OptionBase } from 'vayla-design-lib/menu/menu';
import { isEmpty } from 'utils/string-utils';

const MARGIN_BETWEEN_INPUT_AND_POPUP = 2;

export type DropdownOption<TValue> = {
    type: 'VALUE';
    name: string;
    value: TValue;
} & OptionBase;

export const dropdownOption = <TValue,>(
    value: TValue,
    name: string,
    qaId: string,
    disabled: boolean = false,
): DropdownOption<TValue> => ({
    type: 'VALUE',
    name,
    value,
    disabled,
    qaId,
});

export enum DropdownSize {
    SMALL = 'dropdown--small',
    MEDIUM = 'dropdown--medium',
    LARGE = 'dropdown--large',
    STRETCH = 'dropdown--stretch',
    AUTO = 'dropdown--auto-width',
}

export type Item<TItemValue> = {
    name: string;
    value: TItemValue;
    disabled?: boolean;
    qaId: string;
};

export type DropdownOptions<TItemValue> =
    | Item<TItemValue>[]
    | ((searchTerm: string) => Promise<Item<TItemValue>[]>);

export enum DropdownPopupMode {
    Modal,
    Inline,
}

export type DropdownProps<TItemValue> = {
    unselectText?: string;
    placeholder?: string;
    displaySelectedName?: boolean;
    value?: TItemValue;
    getName?: (value: TItemValue) => string;
    clearable?: boolean; // makes the icon X to clear the dropdown
    canUnselect?: boolean; // adds a "none" option
    searchable?: boolean;
    filter?: (option: Item<TItemValue>, searchTerm: string) => boolean;
    options?: DropdownOptions<TItemValue>;
    onChange?: (item: TItemValue | undefined) => void;
    attachLeft?: boolean;
    attachRight?: boolean;
    size?: DropdownSize;
    wide?: boolean;
    onBlur?: () => void;
    onFocus?: () => void;
    hasError?: boolean;
    onAddClick?: (searchTerm: string) => void;
    onAddClickTitle?: string;
    onAddClickIcon?: IconComponent;
    wideList?: boolean;
    qaId?: string;
    inputRef?: React.RefObject<HTMLInputElement | null>;
    openOverride?: boolean;
    popupMode?: DropdownPopupMode;
    customIcon?: IconComponent;
    useAnchorElementWidth?: boolean;
} & Pick<React.HTMLProps<HTMLInputElement>, 'disabled' | 'title'>;

function isOptionsArray<TItemValue>(
    options: DropdownOptions<TItemValue>,
): options is Item<TItemValue>[] {
    return Array.isArray(options);
}

function nameStartsWith<TItemValue>(option: Item<TItemValue>, searchTerm: string) {
    return option.name.toUpperCase().startsWith(searchTerm.toUpperCase());
}

export function nameIncludes<TItemValue>(option: Item<TItemValue>, searchTerm: string) {
    return option.name.toUpperCase().includes(searchTerm.toUpperCase());
}

export const Dropdown = function <TItemValue>({
    size = DropdownSize.MEDIUM,
    filter = nameStartsWith,
    searchable = true,
    popupMode = DropdownPopupMode.Modal,
    customIcon: CustomIcon = undefined,
    displaySelectedName = true,
    ...props
}: DropdownProps<TItemValue>): React.JSX.Element {
    const { t } = useTranslation();
    const menuRef = React.useRef<HTMLDivElement>(null);
    const inputRefInternal = React.useRef<HTMLInputElement>(null);
    const listRef = React.useRef<HTMLUListElement>(null);
    const focusedOptionRef = React.useRef<HTMLLIElement>(null);
    const optionsIsFunc = props.options && !isOptionsArray(props.options);
    const [loadedOptions, setLoadedOptions] = React.useState<Item<TItemValue>[]>();
    const earlySelect = React.useRef(false);
    const { isLoading, load: loadOptions } = useImmediateLoader((options: Item<TItemValue>[]) => {
        const activeOptions = options.filter((option) => !option.disabled);
        if (earlySelect.current && activeOptions.length === 1) {
            select(first(activeOptions)?.value);
        } else {
            setLoadedOptions(options);
        }
        earlySelect.current = false;
    });
    const [open, setOpen] = React.useState(false);
    const [hasFocus, _setHasFocus] = React.useState(false);
    const [searchTerm, setSearchTerm] = React.useState('');
    const [searchCommitted, setSearchTermCommitted] = React.useState(false);
    const [optionFocusIndex, setOptionFocusIndex] = React.useState(0);
    const showEmptyOption = props.canUnselect && !searchTerm && (props.value || optionsIsFunc);
    const inputRef = props.inputRef ?? inputRefInternal;

    const openOrOverridden = props.openOverride !== undefined ? props.openOverride : open;

    let isMouseDown = false;
    const className = createClassName(
        styles['dropdown'],
        props.disabled && styles['dropdown--disabled'],
        props.attachLeft && styles['dropdown--attach-left'],
        props.attachRight && styles['dropdown--attach-right'],
        size && styles[size],
        hasFocus && styles['dropdown--has-focus'],
        props.wide && styles['dropdown--wide'],
        props.hasError && styles['dropdown--has-error'],
        searchable && styles['dropdown--searchable'],
        props.wideList && styles['dropdown--wide-list'],
    );

    const options =
        props.options === undefined || isOptionsArray(props.options)
            ? props.options
            : loadedOptions;
    const filteredOptions = getFilteredOptions();

    const selectedName =
        (props.value !== undefined && props.getName !== undefined
            ? props.getName(props.value)
            : undefined) ??
        options?.find((item) => item.value === props.value)?.name ??
        '';

    const valueShownInInput = searchCommitted || !displaySelectedName ? searchTerm : selectedName;

    function setHasFocus(value: boolean) {
        if (hasFocus && !value) {
            props.onBlur && props.onBlur();
        } else if (!hasFocus && value) {
            props.onFocus && props.onFocus();
        }
        _setHasFocus(value);
    }

    function focusInput() {
        inputRef.current?.focus();
        inputRef.current?.select();
    }

    function openListAndFocusSelectedItem() {
        setOpen(true);
        focusSelectedItem();
    }

    function focusSelectedItem() {
        if (options) {
            const selectedIndex = filteredOptions.findIndex(
                (option) => option.value === props.value,
            );
            if (selectedIndex === -1) {
                setOptionFocusIndex(showEmptyOption ? -1 : 0);
            } else {
                setOptionFocusIndex(selectedIndex);
            }
        }
    }

    function select(value: TItemValue | undefined) {
        props.onChange && props.onChange(value);
        setOpen(false);
        setSearchTermCommitted(false);
        searchFor('');
    }

    function unselect() {
        select(undefined);
    }

    function handleRootMouseDown() {
        isMouseDown = true;
    }

    function handleItemClick(item: Item<TItemValue>, event: React.MouseEvent<HTMLLIElement>) {
        item.disabled ? event.stopPropagation() : select(item.value);
        focusInput(); // keep focus in this dropdown
    }

    function handleInputFocus() {
        setHasFocus(true);
    }

    function handleInputBlur() {
        if (isMouseDown) {
            // Input blur may happen because a mouse down event in a header or a dropdown list,
            // in this case we don't want to lose focus
            focusInput();
        } else {
            setHasFocus(false);
            setOpen(false);
            setSearchTermCommitted(false);
            searchFor('');
        }
    }

    function handleInputChange(value: string) {
        if (searchable) {
            setSearchTermCommitted(true);
            searchFor(value);
            setOptionFocusIndex(0);
        }
    }

    function getItemClassName(item: Item<TItemValue> | undefined, index: number) {
        return createClassName(
            styles['dropdown__list-item'],
            item?.disabled && styles['dropdown__list-item--disabled'],
            item?.value === props.value && styles['dropdown__list-item--selected'],
            index === optionFocusIndex && styles['dropdown__list-item--focused'],
        );
    }

    function updateOptionFocusIndex(value: number) {
        const extraOptionCount = showEmptyOption ? 1 : 0;
        const optionCount = filteredOptions.length + extraOptionCount;
        if (optionCount > 0) {
            setOptionFocusIndex(
                (Math.max(optionFocusIndex + optionCount + extraOptionCount + value, 0) %
                    optionCount) -
                    extraOptionCount,
            );
        }
    }

    function handleInputKeyPress(e: React.KeyboardEvent<HTMLInputElement>) {
        if (!searchable && e.code === 'Space') {
            openListAndFocusSelectedItem();
            e.preventDefault();
        }
    }

    function handleInputKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
        if (openOrOverridden) {
            switch (e.code) {
                case 'ArrowUp':
                    updateOptionFocusIndex(-1);
                    e.preventDefault();
                    break;
                case 'ArrowDown':
                    updateOptionFocusIndex(1);
                    e.preventDefault();
                    break;
                case 'Tab':
                case 'Enter': {
                    if (optionsIsFunc && isLoading) {
                        earlySelect.current = true;
                    } else {
                        const item = filteredOptions[optionFocusIndex];
                        if (!item?.disabled) {
                            select(item?.value || undefined);
                        }
                    }
                }
            }
        } else {
            switch (e.code) {
                case 'ArrowDown':
                    openListAndFocusSelectedItem();
                    e.preventDefault();
                    break;
                case 'Tab':
                case 'Enter': {
                    if (isEmpty(valueShownInInput)) {
                        select(undefined);
                    }
                }
            }
        }
    }

    function getFilteredOptions(): Item<TItemValue>[] {
        return (
            (searchTerm && props.options && isOptionsArray(props.options)
                ? options?.filter((option) => filter(option, searchTerm))
                : options) || []
        );
    }

    const searchFor = (term: string) => {
        term ? openListAndFocusSelectedItem() : setOpen(false);
        if (props.options && !isOptionsArray(props.options)) {
            loadOptions(props.options(term));
        }
        setSearchTerm(term);
    };

    // Set initial "hasFocus"
    React.useEffect(() => {
        setHasFocus(document.activeElement === inputRef.current);
    });

    // Scroll to focused option
    React.useEffect(() => {
        if (listRef.current && focusedOptionRef.current) {
            const listRect = listRef.current.getBoundingClientRect();
            const optionRect = focusedOptionRef.current.getBoundingClientRect();
            const listBottom = listRect.top + listRect.height;
            const optionBottom = optionRect.top + optionRect.height;
            const below = optionBottom - listBottom;
            if (below >= 0) {
                listRef.current.scrollTop += below;
            } else {
                const above = listRect.top - optionRect.top;
                if (above > 0) {
                    listRef.current.scrollTop -= above;
                }
            }
        }
    }, [openOrOverridden, optionFocusIndex]);

    // When options change, update option focus index
    React.useEffect(() => {
        setOptionFocusIndex(
            options
                ? Math.max(
                      0,
                      options.findIndex((option) => option.value === props.value),
                  )
                : 0,
        );
    }, [options]);

    function renderMenuItems() {
        return (
            <React.Fragment>
                <ul className={styles['dropdown__list']} ref={listRef}>
                    {showEmptyOption && (
                        <li
                            className={getItemClassName(undefined, -1)}
                            onClick={unselect}
                            title={props.unselectText || t('dropdown.unselected')}
                            ref={optionFocusIndex === -1 ? focusedOptionRef : undefined}>
                            <span className={styles['dropdown__list-item-icon']}>
                                <Icons.Selected size={IconSize.SMALL} />
                            </span>
                            <span className={styles['dropdown__list-item-text']}>
                                {props.unselectText || t('dropdown.unselected')}
                            </span>
                        </li>
                    )}
                    {!isLoading &&
                        filteredOptions.map((item, index) => (
                            <li
                                className={getItemClassName(item, index)}
                                key={index}
                                qa-id={item.qaId}
                                onClick={(event) => handleItemClick(item, event)}
                                title={item.name}
                                aria-disabled={!!item.disabled}
                                ref={optionFocusIndex === index ? focusedOptionRef : undefined}>
                                <span className={styles['dropdown__list-item-icon']}>
                                    <Icons.Selected size={IconSize.SMALL} />
                                </span>
                                <span className={styles['dropdown__list-item-text']}>
                                    {item.name}
                                </span>
                            </li>
                        ))}
                    {searchTerm && !isLoading && filteredOptions.length === 0 && (
                        <li
                            title="Ei vaihtoehtoja"
                            className={createClassName(
                                styles['dropdown__list-item'],
                                styles['dropdown__list-item--no-options'],
                            )}>
                            {t('dropdown.no-options')}
                        </li>
                    )}
                    {isLoading && (
                        <li
                            title="Ladataan"
                            className={createClassName(
                                styles['dropdown__list-item'],
                                styles['dropdown__list-item--loading'],
                            )}>
                            {t('dropdown.loading')}
                            <span className={styles['dropdown__loading-indicator']} />
                        </li>
                    )}
                </ul>
                {props.onAddClick && (
                    <div className="dropdown__add-new-container">
                        <Button
                            variant={ButtonVariant.GHOST}
                            icon={props.onAddClickIcon ?? Icons.Append}
                            wide
                            onClick={() => {
                                props.onAddClick && props.onAddClick(valueShownInInput);
                            }}>
                            {props.onAddClickTitle ?? t('dropdown.add-new')}
                        </Button>
                    </div>
                )}
            </React.Fragment>
        );
    }

    return (
        <div
            qa-id={props.qaId}
            className={className}
            ref={menuRef}
            onMouseDown={() => handleRootMouseDown()}>
            <div
                className={styles['dropdown__header']}
                role="button"
                onClick={() => {
                    if (!props.disabled) {
                        focusInput();
                        openOrOverridden ? setOpen(false) : openListAndFocusSelectedItem();
                    }
                }}
                title={props.title ? props.title : selectedName}>
                <div className={styles['dropdown__title']}>
                    <input
                        className={styles['dropdown__input']}
                        ref={inputRef}
                        onFocus={handleInputFocus}
                        onBlur={handleInputBlur}
                        onKeyPress={handleInputKeyPress}
                        onKeyDown={handleInputKeyDown}
                        disabled={props.disabled}
                        value={valueShownInInput}
                        onChange={(e) => handleInputChange(e.target.value)}
                        placeholder={props.placeholder}
                    />
                </div>
                <div className={styles['dropdown__icon']}>
                    {props.clearable && props.value !== undefined ? (
                        <Icons.Close size={IconSize.SMALL} onClick={unselect} />
                    ) : CustomIcon ? (
                        <CustomIcon
                            size={IconSize.SMALL}
                            color={props.disabled ? IconColor.INHERIT : undefined}
                        />
                    ) : searchable && optionsIsFunc ? (
                        <Icons.Search
                            size={IconSize.SMALL}
                            color={props.disabled ? IconColor.INHERIT : undefined}
                        />
                    ) : (
                        <Icons.Down
                            size={IconSize.SMALL}
                            color={props.disabled ? IconColor.INHERIT : undefined}
                        />
                    )}
                </div>
            </div>
            {openOrOverridden &&
                (popupMode === DropdownPopupMode.Modal ? (
                    <CloseableModal
                        useAnchorElementWidth={props.useAnchorElementWidth ?? true}
                        onClickOutside={() => setOpen(false)}
                        className={createClassName(
                            styles['dropdown__list-container'],
                            styles['dropdown__list-container--modal'],
                        )}
                        anchorElementRef={menuRef}
                        margin={MARGIN_BETWEEN_INPUT_AND_POPUP}>
                        {renderMenuItems()}
                    </CloseableModal>
                ) : (
                    <div
                        className={createClassName(
                            styles['dropdown__list-container'],
                            styles['dropdown__list-container--inline'],
                        )}>
                        {renderMenuItems()}
                    </div>
                ))}
        </div>
    );
};
