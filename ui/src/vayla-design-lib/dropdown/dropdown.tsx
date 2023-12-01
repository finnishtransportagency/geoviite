import * as React from 'react';
import styles from './dropdown.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { CloseableModal } from 'vayla-design-lib/closeable-modal/closeable-modal';
import { useImmediateLoader } from 'utils/react-utils';

export enum DropdownSize {
    SMALL = 'dropdown--small',
    MEDIUM = 'dropdown--medium',
    STRETCH = 'dropdown--stretch',
    AUTO = 'dropdown--auto-width',
}

export type Item<TItemValue> = {
    name: string;
    value: TItemValue;
    disabled?: boolean;
    qaId?: string;
};

export type DropdownOptions<TItemValue> =
    | Item<TItemValue>[]
    | ((searchTerm: string) => Promise<Item<TItemValue>[]>);

export type DropdownProps<TItemValue> = {
    unselectText?: string;
    placeholder?: string;
    value?: TItemValue;
    getName?: (value: TItemValue) => string;
    canUnselect?: boolean;
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
    onAddClick?: () => void;
    wideList?: boolean;
    qaId?: string;
} & Pick<React.HTMLProps<HTMLInputElement>, 'disabled'>;

function isOptionsArray<TItemValue>(
    options: DropdownOptions<TItemValue>,
): options is Item<TItemValue>[] {
    return Array.isArray(options);
}

function nameStartsWith<TItemValue>(option: Item<TItemValue>, searchTerm: string) {
    return option.name.toUpperCase().startsWith(searchTerm.toUpperCase());
}

export const Dropdown = function <TItemValue>({
    size = DropdownSize.MEDIUM,
    filter = nameStartsWith,
    searchable = true,
    ...props
}: DropdownProps<TItemValue>): JSX.Element {
    const menuRef = React.useRef<HTMLDivElement>(null);
    const inputRef = React.useRef<HTMLInputElement>(null);
    const listRef = React.useRef<HTMLUListElement>(null);
    const focusedOptionRef = React.useRef<HTMLLIElement>(null);
    const optionsIsFunc = props.options && !isOptionsArray(props.options);
    const [loadedOptions, setLoadedOptions] = React.useState<Item<TItemValue>[]>();
    const earlySelect = React.useRef(false);
    const { isLoading, load: loadOptions } = useImmediateLoader((options: Item<TItemValue>[]) => {
        const activeOptions = options.filter((option) => !option.disabled);
        if (earlySelect.current && activeOptions.length === 1) {
            select(activeOptions[0].value);
        } else {
            setLoadedOptions(options);
        }
        earlySelect.current = false;
    });
    const [open, setOpen] = React.useState(false);
    const [hasFocus, _setHasFocus] = React.useState(false);
    const [searchTerm, setSearchTerm] = React.useState('');
    const [optionFocusIndex, setOptionFocusIndex] = React.useState(0);
    const showEmptyOption = props.canUnselect && !searchTerm && (props.value || optionsIsFunc);

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

    const selectedName = props.value
        ? (props.getName && props.getName(props.value)) ||
          options?.find((item) => item.value == props.value)?.name ||
          ''
        : props.placeholder;

    const selectedQaId = options?.find((i) => i.value == props.value)?.qaId;

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
    }

    function openList() {
        setOpen(true);
        if (options) {
            const selectedIndex = filteredOptions.findIndex(
                (option) => option.value == props.value,
            );
            if (selectedIndex == -1) {
                setOptionFocusIndex(showEmptyOption ? -1 : 0);
            } else {
                setOptionFocusIndex(selectedIndex);
            }
        }
    }

    function closeList() {
        setOpen(false);
    }

    function select(value: TItemValue | undefined) {
        props.onChange && props.onChange(value);
        closeList();
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
            closeList();
            searchFor('');
        }
    }

    function handleInputChange(value: string) {
        if (searchable) {
            searchFor(value);
            setOptionFocusIndex(0);
        }
    }

    function getItemClassName(item: Item<TItemValue> | undefined, index: number) {
        return createClassName(
            styles['dropdown__list-item'],
            item?.disabled && styles['dropdown__list-item--disabled'],
            item?.value == props.value && styles['dropdown__list-item--selected'],
            index == optionFocusIndex && styles['dropdown__list-item--focused'],
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
        if (!searchable && e.code == 'Space') {
            openList();
            e.preventDefault();
        }
    }

    function handleInputKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
        if (open) {
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
                    openList();
                    e.preventDefault();
                    break;
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
        term ? openList() : closeList();
        if (props.options && !isOptionsArray(props.options)) {
            loadOptions(props.options(term));
        }
        setSearchTerm(term);
    };

    // Set initial "hasFocus"
    React.useEffect(() => {
        setHasFocus(document.activeElement == inputRef.current);
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
    });

    // When options change, update option focus index
    React.useEffect(() => {
        setOptionFocusIndex(
            options
                ? Math.max(
                      0,
                      options.findIndex((option) => option.value == props.value),
                  )
                : 0,
        );
    }, [options]);

    return (
        <div
            qa-id={props.qaId}
            className={className}
            ref={menuRef}
            onMouseDown={() => handleRootMouseDown()}>
            <div
                className={styles['dropdown__header']}
                role="button"
                onClick={(e) => {
                    if (!props.disabled) {
                        e.stopPropagation();
                        focusInput();
                        open ? closeList() : openList();
                    }
                }}
                title={selectedName}>
                <div className={styles['dropdown__title']}>
                    <input
                        className={styles['dropdown__input']}
                        ref={inputRef}
                        onFocus={handleInputFocus}
                        onBlur={handleInputBlur}
                        onKeyPress={handleInputKeyPress}
                        onKeyDown={handleInputKeyDown}
                        disabled={props.disabled}
                        value={searchTerm}
                        onChange={(e) => handleInputChange(e.target.value)}
                    />
                    {!searchTerm && (
                        <div className={styles['dropdown__current-value']} qa-id={selectedQaId}>
                            <span>{selectedName}</span>
                        </div>
                    )}
                </div>
                <div className={styles['dropdown__icon']}>
                    {searchable && optionsIsFunc ? (
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
            {open && (
                <CloseableModal
                    useRefWidth
                    onClickOutside={closeList}
                    className={styles['dropdown__list-container']}
                    offsetY={36}
                    positionRef={menuRef}>
                    <ul className={styles['dropdown__list']} ref={listRef}>
                        {showEmptyOption && (
                            <li
                                className={getItemClassName(undefined, -1)}
                                onClick={unselect}
                                title={props.unselectText || 'Ei valittu'}
                                ref={optionFocusIndex == -1 ? focusedOptionRef : undefined}>
                                <span className={styles['dropdown__list-item-icon']}>
                                    <Icons.Selected size={IconSize.SMALL} />
                                </span>
                                <span className={styles['dropdown__list-item-text']}>
                                    {props.unselectText || 'Ei valittu'}
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
                                    ref={optionFocusIndex == index ? focusedOptionRef : undefined}>
                                    <span className={styles['dropdown__list-item-icon']}>
                                        <Icons.Selected size={IconSize.SMALL} />
                                    </span>
                                    <span className={styles['dropdown__list-item-text']}>
                                        {item.name}
                                    </span>
                                </li>
                            ))}
                        {searchTerm && !isLoading && filteredOptions.length == 0 && (
                            <li
                                title="Ei vaihtoehtoja"
                                className={createClassName(
                                    styles['dropdown__list-item'],
                                    styles['dropdown__list-item--no-options'],
                                )}>
                                Ei vaihtoehtoja
                            </li>
                        )}
                        {isLoading && (
                            <li
                                title="Ladataan"
                                className={createClassName(
                                    styles['dropdown__list-item'],
                                    styles['dropdown__list-item--loading'],
                                )}>
                                Ladataan
                                <span className={styles['dropdown__loading-indicator']} />
                            </li>
                        )}
                    </ul>
                    {props.onAddClick && (
                        <div className="dropdown__add-new-container">
                            <Button
                                variant={ButtonVariant.GHOST}
                                icon={Icons.Append}
                                wide
                                onClick={props.onAddClick}>
                                Lisää uusi
                            </Button>
                        </div>
                    )}
                </CloseableModal>
            )}
        </div>
    );
};
