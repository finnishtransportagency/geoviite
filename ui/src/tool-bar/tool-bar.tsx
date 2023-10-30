import * as React from 'react';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Dropdown, DropdownSize, Item } from 'vayla-design-lib/dropdown/dropdown';
import { getSwitchesBySearchTerm } from 'track-layout/layout-switch-api';
import { getSwitch } from 'track-layout/layout-switch-api';
import { getKmPost } from 'track-layout/layout-km-post-api';
import {
    getLocationTrackDescriptions,
} from 'track-layout/layout-location-track-api';
import {
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutTrackNumber,
} from 'track-layout/track-layout-model';
import { debounceAsync } from 'utils/async-utils';
import { isNilOrBlank } from 'utils/string-utils';
import {
    BoundingBox,
    boundingBoxAroundPoints,
    centerForBoundingBox,
    expandBoundingBox,
} from 'model/geometry';
import { useTranslation } from 'react-i18next';
import { PublishType } from 'common/common-model';
import styles from './tool-bar.scss';
import { LocationTrackEditDialog } from 'tool-panel/location-track/dialog/location-track-edit-dialog';
import { SwitchEditDialog } from 'tool-panel/switch/dialog/switch-edit-dialog';
import { KmPostEditDialogContainer } from 'tool-panel/km-post/dialog/km-post-edit-dialog';
import { TrackNumberEditDialogContainer } from 'tool-panel/track-number/dialog/track-number-edit-dialog';
import { Menu } from 'vayla-design-lib/menu/menu';
import { ChangeTimes } from 'common/common-slice';
import { WriteAccessRequired } from 'user/write-access-required';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { MapLayerMenu } from 'map/layer-menu/map-layer-menu';
import { MapLayerMenuChange, MapLayerMenuGroups, MapLayerName } from 'map/map-model';
import { getTrackNumberReferenceLine } from 'track-layout/layout-reference-line-api';
import { OnSelectFunction, OptionalUnselectableItemCollections } from 'selection/selection-model';
import {
    refereshKmPostSelection,
    refreshLocationTrackSelection,
    refreshSwitchSelection,
    refreshTrackNumberSelection,
} from 'track-layout/track-layout-react-utils';
import { getBySearchTerm } from 'track-layout/track-layout-search-api';

export type ToolbarParams = {
    onSelect: OnSelectFunction;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    onPublishTypeChange: (publishType: PublishType) => void;
    onOpenPreview: () => void;
    showArea: (area: BoundingBox) => void;
    publishType: PublishType;
    changeTimes: ChangeTimes;
    onStopLinking: () => void;
    disableNewMenu: boolean;
    onMapLayerChange: (change: MapLayerMenuChange) => void;
    mapLayerMenuGroups: MapLayerMenuGroups;
    visibleLayers: MapLayerName[];
};

type LocationTrackItemValue = {
    locationTrack: LayoutLocationTrack;
    type: 'locationTrackSearchItem';
};

type SwitchItemValue = {
    layoutSwitch: LayoutSwitch;
    type: 'switchSearchItem';
};

type TrackNumberItemValue = {
    trackNumber: LayoutTrackNumber;
    type: 'trackNumberSearchItem';
};

function createTrackNumberItem(layoutTrackNumber: LayoutTrackNumber): Item<TrackNumberItemValue> {
    return {
        name: layoutTrackNumber.number,
        value: {
            type: 'trackNumberSearchItem',
            trackNumber: layoutTrackNumber,
        },
    };
}

type SearchItemValue = LocationTrackItemValue | SwitchItemValue | TrackNumberItemValue;

async function getOptions(
    publishType: PublishType,
    searchTerm: string,
): Promise<Item<SearchItemValue>[]> {
    if (isNilOrBlank(searchTerm)) {
        return Promise.resolve([]);
    }

    const searchResult = await getBySearchTerm(searchTerm, publishType);

    const locationTrackDescriptions = await getLocationTrackDescriptions(
        searchResult.locationTracks.map((lt) => lt.id),
        publishType,
    );

    const locationTracks: Item<LocationTrackItemValue>[] = searchResult.locationTracks.map(
        (locationTrack) => ({
            name: `${locationTrack.name}, ${
                (locationTrackDescriptions &&
                    locationTrackDescriptions.find((d) => d.id == locationTrack.id)?.description) ??
                ''
            }`,
            value: {
                type: 'locationTrackSearchItem',
                locationTrack: locationTrack,
            },
        }),
    );

    const switches: Item<SwitchItemValue>[] = searchResult.switches.map((layoutSwitch) => ({
        name: `${layoutSwitch.name}`,
        value: {
            type: 'switchSearchItem',
            layoutSwitch: layoutSwitch,
        },
    }));

    const trackNumbers: Item<TrackNumberItemValue>[] =
        searchResult.trackNumbers.map(createTrackNumberItem);

    return await Promise.all([locationTracks, switches, trackNumbers]).then((results) =>
        results.flat(),
    );
}

export const ToolBar: React.FC<ToolbarParams> = ({
    onSelect,
    onUnselect,
    onPublishTypeChange,
    onOpenPreview,
    showArea,
    publishType,
    changeTimes,
    onStopLinking,
    disableNewMenu,
    onMapLayerChange,
    mapLayerMenuGroups,
    visibleLayers,
}: ToolbarParams) => {
    const { t } = useTranslation();

    const [showAddMenu, setShowAddMenu] = React.useState(false);
    const [showAddTrackNumberDialog, setShowAddTrackNumberDialog] = React.useState(false);
    const [showAddSwitchDialog, setShowAddSwitchDialog] = React.useState(false);
    const [showAddLocationTrackDialog, setShowAddLocationTrackDialog] = React.useState(false);
    const [showAddKmPostDialog, setShowAddKmPostDialog] = React.useState(false);
    const menuRef = React.useRef(null);

    enum NewMenuItems {
        'trackNumber' = 1,
        'locationTrack' = 2,
        'switch' = 3,
        'kmPost' = 4,
    }

    const newMenuItems = [
        { value: NewMenuItems.trackNumber, name: t('tool-bar.new-track-number') },
        { value: NewMenuItems.locationTrack, name: t('tool-bar.new-location-track') },
        { value: NewMenuItems.switch, name: t('tool-bar.new-switch') },
        { value: NewMenuItems.kmPost, name: t('tool-bar.new-km-post') },
    ];

    const handleNewMenuItemChange = (item: NewMenuItems) => {
        showAddDialog(item);
    };

    // Use debounced function to collect keystrokes before triggering a search
    const debouncedGetOptions = debounceAsync(getOptions, 250);
    // Use memoized function to make debouncing functionality to work when re-rendering
    const memoizedDebouncedGetOptions = React.useCallback(
        (searchTerm: string) => debouncedGetOptions(publishType, searchTerm),
        [publishType],
    );

    function onItemSelected(item: SearchItemValue | undefined) {
        switch (item?.type) {
            case 'locationTrackSearchItem':
                item.locationTrack.boundingBox && showArea(item.locationTrack.boundingBox);
                return onSelect({ locationTracks: [item.locationTrack.id] });

            case 'switchSearchItem':
                if (item.layoutSwitch.joints.length > 0) {
                    const center = centerForBoundingBox(
                        boundingBoxAroundPoints(
                            item.layoutSwitch.joints.map((joint) => joint.location),
                        ),
                    );
                    const bbox = expandBoundingBox(boundingBoxAroundPoints([center]), 200);
                    showArea(bbox);
                }
                return onSelect({ switches: [item.layoutSwitch.id] });

            case 'trackNumberSearchItem':
                getTrackNumberReferenceLine(item.trackNumber.id, publishType).then((referenceLine) => {
                    if (referenceLine?.boundingBox) {
                        showArea(referenceLine.boundingBox);
                    }
                });

                return onSelect({ trackNumbers: [item.trackNumber.id] });

            default:
                return;
        }
    }

    function showAddDialog(dialog: NewMenuItems) {
        switch (dialog) {
            case NewMenuItems.trackNumber:
                setShowAddTrackNumberDialog(true);
                break;
            case NewMenuItems.locationTrack:
                setShowAddLocationTrackDialog(true);
                break;
            case NewMenuItems.switch:
                setShowAddSwitchDialog(true);
                break;
            case NewMenuItems.kmPost:
                setShowAddKmPostDialog(true);
                break;
            default:
                return exhaustiveMatchingGuard(dialog);
        }

        setShowAddMenu(false);
    }

    function moveToOfficialPublishType() {
        onPublishTypeChange('OFFICIAL');
        setShowAddMenu(false);
    }

    function openPreviewAndStopLinking() {
        onOpenPreview();
        onStopLinking();
    }

    const handleTrackNumberSave = refreshTrackNumberSelection('DRAFT', onSelect, onUnselect);
    const handleLocationTrackSave = refreshLocationTrackSelection('DRAFT', onSelect, onUnselect);
    const handleSwitchSave = refreshSwitchSelection('DRAFT', onSelect, onUnselect);
    const handleKmPostSave = refereshKmPostSelection('DRAFT', onSelect, onUnselect);

    return (
        <div className={`tool-bar tool-bar--${publishType.toLowerCase()}`}>
            <div className={styles['tool-bar__left-section']}>
                <Dropdown
                    placeholder={t('tool-bar.search')}
                    options={memoizedDebouncedGetOptions}
                    searchable
                    onChange={onItemSelected}
                    size={DropdownSize.STRETCH}
                    wideList
                    wide
                    qa-id="search-box"
                />
                <MapLayerMenu
                    onMenuChange={onMapLayerChange}
                    mapLayerMenuGroups={mapLayerMenuGroups}
                    visibleLayers={visibleLayers}
                />
                <div className={styles['tool-bar__new-menu-button']}>
                    <WriteAccessRequired>
                        <Button
                            ref={menuRef}
                            title={t('tool-bar.new')}
                            variant={ButtonVariant.SECONDARY}
                            icon={Icons.Append}
                            disabled={publishType !== 'DRAFT' || disableNewMenu}
                            onClick={() => setShowAddMenu(!showAddMenu)}
                        />
                    </WriteAccessRequired>
                </div>
            </div>

            <div className={styles['tool-bar__middle-section']}>
                {publishType === 'DRAFT' && t('tool-bar.draft-mode.title')}
            </div>

            <div className={styles['tool-bar__right-section']}>
                {publishType === 'OFFICIAL' && (
                    <WriteAccessRequired>
                        <Button
                            variant={ButtonVariant.PRIMARY}
                            onClick={() => onPublishTypeChange('DRAFT')}>
                            {t('tool-bar.draft-mode.enable')}
                        </Button>
                    </WriteAccessRequired>
                )}
                {publishType === 'DRAFT' && (
                    <React.Fragment>
                        <Button
                            variant={ButtonVariant.SECONDARY}
                            onClick={() => moveToOfficialPublishType()}>
                            {t('tool-bar.draft-mode.disable')}
                        </Button>
                        <Button
                            icon={Icons.VectorRight}
                            variant={ButtonVariant.PRIMARY}
                            onClick={() => openPreviewAndStopLinking()}>
                            {t('tool-bar.preview-mode.enable')}
                        </Button>
                    </React.Fragment>
                )}
            </div>

            {showAddMenu && (
                <Menu
                    positionRef={menuRef}
                    items={newMenuItems}
                    onSelect={(item) => item && handleNewMenuItemChange(item)}
                    onClickOutside={() => setShowAddMenu(false)}
                />
            )}

            {showAddTrackNumberDialog && (
                <TrackNumberEditDialogContainer
                    onClose={() => setShowAddTrackNumberDialog(false)}
                    onSave={handleTrackNumberSave}
                />
            )}
            {showAddLocationTrackDialog && (
                <LocationTrackEditDialog
                    onClose={() => setShowAddLocationTrackDialog(false)}
                    onSave={handleLocationTrackSave}
                    locationTrackChangeTime={changeTimes.layoutLocationTrack}
                />
            )}

            {showAddSwitchDialog && (
                <SwitchEditDialog
                    onClose={() => setShowAddSwitchDialog(false)}
                    onSave={handleSwitchSave}
                />
            )}

            {showAddKmPostDialog && (
                <KmPostEditDialogContainer
                    onClose={() => setShowAddKmPostDialog(false)}
                    onSave={handleKmPostSave}
                />
            )}
        </div>
    );
};
