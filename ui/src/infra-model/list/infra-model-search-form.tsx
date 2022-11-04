import * as React from 'react';
import { GeometryPlanSearchParams, PlanSource } from 'geometry/geometry-model';
import Select, { components as reactSelectComponents, GroupBase, OptionProps } from 'react-select';
import { LayoutTrackNumber, TrafficOperatingPoint } from 'track-layout/track-layout-model';
import { deduplicate, filterNotEmpty } from 'utils/array-utils';
import { InfraModelLoadButtonContainer } from 'infra-model/list/infra-model-load-button-container';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { planSources } from 'utils/enum-localization-utils';

export type InframodelSearchFormProps = {
    trackNumbers: LayoutTrackNumber[];
    searchParams: GeometryPlanSearchParams;
    onSearchParamsChange: (searchParams: GeometryPlanSearchParams) => void;
};

type OptionBase = {
    id?: string;
};

type FreeTextOption = OptionBase & {
    type: 'FREE_TEXT';
    label: string;
    value: string;
};

type TrackNumberOption = OptionBase & {
    type: 'TRACK_NUMBER_OPTION';
    label: string;
    value: LayoutTrackNumber;
};

type TrafficOperatingPointOption = OptionBase & {
    type: 'TRAFFIC_OPERATING_POINT_OPTION';
    label: string;
    value: TrafficOperatingPoint;
};

type Option = TrackNumberOption | TrafficOperatingPointOption | FreeTextOption;

type OptionGroup<TOption> = GroupBase<TOption> & {
    id: string;
    optionsCount?: number;
};

function isGroup(option: Option | GroupBase<Option>): option is GroupBase<Option> {
    return (option as GroupBase<Option>).options !== undefined;
}

type Options = ReadonlyArray<OptionGroup<Option> | Option>;

type GroupShowOptions = {
    limit: number;
};

const defaultGroupShowOptions: GroupShowOptions = {
    limit: 15,
};

type GroupShowOptionsById = {
    [key: string]: GroupShowOptions;
};

const trackNumberPrefix = 'ratanumero: ';

function getTrackNumberOption(trackNumber: LayoutTrackNumber): TrackNumberOption {
    return {
        type: 'TRACK_NUMBER_OPTION',
        label: trackNumberPrefix + trackNumber.number,
        value: trackNumber,
    };
}

function createSearchOptions(trackNumberOptions: TrackNumberOption[], searchTerm: string): Options {
    const matchingOptions = trackNumberOptions
        .filter((tnOpt) => tnOpt.value.number.startsWith(searchTerm))
        .sort((a, b) => a.value.number.localeCompare(b.value.number));
    return [
        ...(matchingOptions.length
            ? [
                  {
                      id: 'track-number',
                      label: 'Ratanumerot',
                      options: matchingOptions,
                  },
              ]
            : []),
    ];
}

function getSearchParamsBySelectVal(
    searchParams: GeometryPlanSearchParams,
    values: Options,
): GeometryPlanSearchParams {
    const trackNumberIds = values
        .map((v: Option) => (v.type == 'TRACK_NUMBER_OPTION' && v.value.id) || null)
        .filter(filterNotEmpty);
    return {
        ...searchParams,
        freeText: '',
        trackNumberIds: trackNumberIds,
    };
}

function getSelectOptionsBySearchParams(
    searchParams: GeometryPlanSearchParams,
    options: TrackNumberOption[],
) {
    return searchParams.trackNumberIds
        .map((id) => options.find((opt) => opt.type == 'TRACK_NUMBER_OPTION' && opt.value.id == id))
        .filter(filterNotEmpty);
}

/**
 * Custom option component is used to inject a custom class name.
 */
function CustomOption<
    OptionType,
    IsMulti extends boolean,
    GroupType extends GroupBase<OptionType> = GroupBase<OptionType>,
>(props: OptionProps<OptionType, IsMulti, GroupType>) {
    return (
        <reactSelectComponents.Option className="custom-react-select-option" {...props}>
            {props.children}
        </reactSelectComponents.Option>
    );
}

export const InfraModelSearchForm: React.FC<InframodelSearchFormProps> = (
    props: InframodelSearchFormProps,
) => {
    const trackNumberOptions = props.trackNumbers.map(getTrackNumberOption);
    const [options, setOptions] = React.useState<Options>([]);
    const [groupShowOptionsById, setGroupShowOptionsById] = React.useState<GroupShowOptionsById>(
        {},
    );

    function loadOptions(text: string) {
        setGroupShowOptionsById({});
        if (text) {
            const refinedOptions = createSearchOptions(trackNumberOptions, text).map((option) => {
                if (isGroup(option)) {
                    return {
                        ...option,
                        optionsCount: option.options.length,
                    };
                }
                return option;
            });
            setOptions(refinedOptions);
        } else {
            setOptions([]);
        }
    }

    function getGroupShowOptions(group: OptionGroup<Option>) {
        return groupShowOptionsById[group.id] || defaultGroupShowOptions;
    }

    function getFilteredOptions(): Options {
        return options.map((option) => {
            if (isGroup(option)) {
                const groupShowOptions = getGroupShowOptions(option);
                return {
                    ...option,
                    options: option.options.slice(0, groupShowOptions.limit),
                };
            }
            return option;
        });
    }

    function setSource(source: PlanSource, active: boolean) {
        props.onSearchParamsChange({
            ...props.searchParams,
            sources: active
                ? deduplicate(props.searchParams.sources.concat(source))
                : props.searchParams.sources.filter((s) => s !== source),
        });
    }

    return (
        <div className="infra-model-search-form">
            <div className="infra-model-search-form__sources-selection">
                {planSources.map((source) => {
                    return (
                        <Checkbox
                            checked={props.searchParams.sources.includes(source.value)}
                            key={source.value}
                            onChange={(e) => setSource(source.value, e.target.checked)}>
                            {source.name}
                        </Checkbox>
                    );
                })}
            </div>

            <div className="infra-model-search-form__auto-complete">
                <Select
                    value={getSelectOptionsBySearchParams(props.searchParams, trackNumberOptions)}
                    inputValue={props.searchParams.freeText}
                    options={getFilteredOptions()}
                    noOptionsMessage={() => null}
                    loadingMessage={() => null}
                    isMulti
                    placeholder={'Hae...'}
                    components={{
                        // Custom behavior for group and options
                        // MenuList: CustomMenuList,
                        Option: CustomOption,
                        // Do not show dropdown/indicator components at all
                        DropdownIndicator: () => null,
                        IndicatorSeparator: () => null,
                    }}
                    onInputChange={(val, action) => {
                        loadOptions(val);
                        if (action.action == 'input-change') {
                            props.onSearchParamsChange({
                                ...props.searchParams,
                                freeText: val,
                            });
                        }
                    }}
                    onChange={(val) => {
                        const selectedOptions = [val].flat() as Option[];
                        props.onSearchParamsChange(
                            getSearchParamsBySelectVal(props.searchParams, selectedOptions),
                        );
                    }}
                />
            </div>

            <div className="infra-model-search-form__last">
                <InfraModelLoadButtonContainer />
            </div>
        </div>
    );
};
