import * as React from 'react';
import { GeometryPlanSearchParams, PlanSource } from 'geometry/geometry-model';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { deduplicate, filterNotEmpty } from 'utils/array-utils';
import { InfraModelLoadButtonContainer } from 'infra-model/list/infra-model-load-button-container';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { planSources } from 'utils/enum-localization-utils';
import Multiselect from 'react-widgets/cjs/Multiselect';
import { useTranslation } from 'react-i18next';

export type InframodelSearchFormProps = {
    trackNumbers: LayoutTrackNumber[];
    searchParams: GeometryPlanSearchParams;
    onSearchParamsChange: (searchParams: GeometryPlanSearchParams) => void;
};

type TrackNumberOption = {
    id?: string;
    typeLabel: string;
    label: string;
    number: string;
    value: LayoutTrackNumber;
};

const trackNumberPrefix = 'ratanumero: ';

export const InfraModelSearchForm: React.FC<InframodelSearchFormProps> = (
    props: InframodelSearchFormProps,
) => {
    const { t } = useTranslation();

    function getTrackNumberOption(trackNumber: LayoutTrackNumber): TrackNumberOption {
        return {
            typeLabel: t('im-form.track-numbers'),
            label: trackNumberPrefix + trackNumber.number,
            number: trackNumber.number,
            id: trackNumber.id,
            value: trackNumber,
        };
    }
    const trackNumberOptions = props.trackNumbers.map(getTrackNumberOption);
    const currentlySelectedTrackNumbers = trackNumberOptions.filter(
        (tn) => tn.id && props.searchParams.trackNumberIds.includes(tn.id),
    );
    const [searchTerm, setSearchTerm] = React.useState('');

    function setSource(source: PlanSource, active: boolean) {
        props.onSearchParamsChange({
            ...props.searchParams,
            sources: active
                ? deduplicate(props.searchParams.sources.concat(source))
                : props.searchParams.sources.filter((s) => s !== source),
        });
    }

    function getSearchParamsBySelectVal(
        searchParams: GeometryPlanSearchParams,
        values: TrackNumberOption[],
    ): GeometryPlanSearchParams {
        const trackNumberIds = values.map((v) => v.value.id || null).filter(filterNotEmpty);
        return {
            ...searchParams,
            freeText: '',
            trackNumberIds: trackNumberIds,
        };
    }

    const trackNumberStartsWith = (searchTerm: string, trackNumber: TrackNumberOption) =>
        trackNumber.number.startsWith(searchTerm);

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
                <Multiselect
                    value={currentlySelectedTrackNumbers}
                    onSearch={(searchTerm, metadata) => {
                        setSearchTerm(searchTerm);
                        if (metadata.action === 'input') {
                            props.onSearchParamsChange({
                                ...props.searchParams,
                                freeText: searchTerm,
                            });
                        }
                    }}
                    onChange={(tags) => {
                        props.onSearchParamsChange(
                            getSearchParamsBySelectVal(props.searchParams, tags),
                        );
                    }}
                    open={
                        searchTerm.length > 0 &&
                        trackNumberOptions.some((item) => trackNumberStartsWith(searchTerm, item))
                    }
                    data={trackNumberOptions}
                    groupBy={'typeLabel'}
                    dataKey={'id'}
                    textField={'label'}
                    placeholder={t('im-form.search')}
                    selectIcon={null}
                    filter={(item, searchTerm) => trackNumberStartsWith(searchTerm, item)}
                />
            </div>

            <div className="infra-model-search-form__last">
                <InfraModelLoadButtonContainer />
            </div>
        </div>
    );
};
