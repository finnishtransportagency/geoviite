import * as React from 'react';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Dropdown, DropdownSize, Item } from 'vayla-design-lib/dropdown/dropdown';
import { getSwitch, getSwitchesBySearchTerm } from 'track-layout/layout-switch-api';
import { getKmPost } from 'track-layout/layout-km-post-api';
import {
    getLocationTrack,
    getLocationTrackDescriptions,
    getLocationTracksBySearchTerm,
} from 'track-layout/layout-location-track-api';
import {
    LayoutKmPostId,
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
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
import {
    updateKmPostChangeTime,
    updateLocationTrackChangeTime,
    updateReferenceLineChangeTime,
    updateTrackNumberChangeTime,
} from 'common/change-time-api';
import { SwitchEditDialog } from 'tool-panel/switch/dialog/switch-edit-dialog';
import { KmPostEditDialog } from 'tool-panel/km-post/dialog/km-post-edit-dialog';
import { TrackNumberEditDialogContainer } from 'tool-panel/track-number/dialog/track-number-edit-dialog';
import { Menu } from 'vayla-design-lib/menu/menu';
import { ChangeTimes } from 'common/common-slice';
import { WriteAccessRequired } from 'user/write-access-required';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

export type ToolbarParams = {
    onSelectTrackNumber: (trackNumberId: LayoutTrackNumberId) => void;
    onSelectLocationTrack: (locationTrackId: LocationTrackId) => void;
    onSelectSwitch: (switchId: LayoutSwitchId) => void;
    onSelectKmPost: (kmPostId: LayoutKmPostId) => void;
    onMapLayerVisibilityChange: (visible: boolean) => void;
    onPublishTypeChange: (publishType: PublishType) => void;
    onOpenPreview: () => void;
    layerMenuVisible: boolean;
    showArea: (area: BoundingBox) => void;
    publishType: PublishType;
    changeTimes: ChangeTimes;
    onStopLinking: () => void;
    disableNewMenu: boolean;
};

type LocationTrackItemValue = {
    locationTrack: LayoutLocationTrack;
    type: 'locationTrackSearchItem';
};

type SwitchItemValue = {
    layoutSwitch: LayoutSwitch;
    type: 'switchSearchItem';
};

type SearchItemValue = LocationTrackItemValue | SwitchItemValue;

async function getOptions(
    publishType: PublishType,
    searchTerm: string,
): Promise<Item<SearchItemValue>[]> {
    if (isNilOrBlank(searchTerm)) {
        return Promise.resolve([]);
    }

    const locationTracks: Promise<Item<LocationTrackItemValue>[]> = getLocationTracksBySearchTerm(
        searchTerm,
        publishType,
        10,
    ).then(async (locationTracks) => {
        const descriptions = await getLocationTrackDescriptions(
            locationTracks.map((lt) => lt.id),
            publishType,
        );
        return locationTracks.map((locationTrack) => ({
            name: `${locationTrack.name}, ${
                (descriptions && descriptions.find((d) => d.id == locationTrack.id)?.description) ??
                ''
            }`,
            value: {
                type: 'locationTrackSearchItem',
                locationTrack: locationTrack,
            },
        }));
    });
    const switches: Promise<Item<SwitchItemValue>[]> = getSwitchesBySearchTerm(
        searchTerm,
        publishType,
        10,
    ).then((switches) => {
        return switches.map((layoutSwitch) => ({
            name: `${layoutSwitch.name}`,
            value: {
                type: 'switchSearchItem',
                layoutSwitch: layoutSwitch,
            },
        }));
    });
    const result = await Promise.all([locationTracks, switches]);
    return [...result[0], ...result[1]];
}

export const ToolBar: React.FC<ToolbarParams> = (props: ToolbarParams) => {
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
        (searchTerm: string) => debouncedGetOptions(props.publishType, searchTerm),
        [props.publishType],
    );

    function onItemSelected(item: SearchItemValue | undefined) {
        if (item?.type == 'locationTrackSearchItem') {
            item.locationTrack.boundingBox && props.showArea(item.locationTrack.boundingBox);
            props.onSelectLocationTrack(item.locationTrack.id);
        } else if (item?.type == 'switchSearchItem') {
            if (item.layoutSwitch.joints.length > 0) {
                const center = centerForBoundingBox(
                    boundingBoxAroundPoints(
                        item.layoutSwitch.joints.map((joint) => joint.location),
                    ),
                );
                const bbox = expandBoundingBox(boundingBoxAroundPoints([center]), 200);
                props.showArea(bbox);
            }
            props.onSelectSwitch(item.layoutSwitch.id);
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

    function handleTrackNumberSave(id: LayoutTrackNumberId) {
        updateReferenceLineChangeTime().then(() =>
            updateTrackNumberChangeTime().then(() => props.onSelectTrackNumber(id)),
        );
    }

    function handleLocationTrackInsert(id: LocationTrackId) {
        updateLocationTrackChangeTime().then((ts) => {
            getLocationTrack(id, 'DRAFT', ts).then((locationTrack) => {
                if (locationTrack) props.onSelectLocationTrack(locationTrack.id);
            });

            setShowAddLocationTrackDialog(false);
        });
    }

    function handleSwitchInsert(switchId: LayoutSwitchId) {
        getSwitch(switchId, 'DRAFT').then((s) => {
            if (s) props.onSelectSwitch(s.id);
        });

        setShowAddSwitchDialog(false);
    }

    function handleKmPostInsert(id: LayoutKmPostId) {
        updateKmPostChangeTime().then((kp) => {
            getKmPost(id, 'DRAFT', kp).then((kmPost) => {
                if (kmPost) props.onSelectKmPost(kmPost.id);
            });
            setShowAddKmPostDialog(false);
        });

        setShowAddKmPostDialog(false);
    }

    function moveToOfficialPublishType() {
        props.onPublishTypeChange('OFFICIAL');
        setShowAddMenu(false);
    }

    function openPreviewAndStopLinking() {
        props.onOpenPreview();
        props.onStopLinking();
    }

    return (
        <div className={`tool-bar tool-bar--${props.publishType.toLowerCase()}`}>
            <div className={styles['tool-bar__left-section']}>
                <Dropdown
                    placeholder={t('tool-bar.search')}
                    options={memoizedDebouncedGetOptions}
                    searchable
                    onChange={onItemSelected}
                    size={DropdownSize.STRETCH}
                    wideList
                    qa-id="search-box"
                />
                <Button
                    variant={ButtonVariant.SECONDARY}
                    icon={Icons.Layers}
                    isPressed={props.layerMenuVisible}
                    onClick={() => props.onMapLayerVisibilityChange(!props.layerMenuVisible)}
                    qa-id="map-layers-button"
                />
                <div className={styles['tool-bar__new-menu-button']} ref={menuRef}>
                    <WriteAccessRequired>
                        <Button
                            variant={ButtonVariant.SECONDARY}
                            icon={Icons.Append}
                            disabled={props.publishType !== 'DRAFT' || props.disableNewMenu}
                            onClick={() => setShowAddMenu(!showAddMenu)}
                        />
                    </WriteAccessRequired>
                </div>
            </div>

            <div className={styles['tool-bar__middle-section']}>
                {props.publishType === 'DRAFT' && t('tool-bar.draft-mode.title')}
            </div>

            <div className={styles['tool-bar__right-section']}>
                {props.publishType === 'OFFICIAL' && (
                    <WriteAccessRequired>
                        <Button
                            variant={ButtonVariant.PRIMARY}
                            onClick={() => props.onPublishTypeChange('DRAFT')}>
                            {t('tool-bar.draft-mode.enable')}
                        </Button>
                    </WriteAccessRequired>
                )}
                {props.publishType === 'DRAFT' && (
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
                    onInsert={handleLocationTrackInsert}
                    locationTrackChangeTime={props.changeTimes.layoutLocationTrack}
                    publishType={'DRAFT'}
                />
            )}

            {showAddSwitchDialog && (
                <SwitchEditDialog
                    onClose={() => setShowAddSwitchDialog(false)}
                    onInsert={handleSwitchInsert}
                />
            )}

            {showAddKmPostDialog && (
                <KmPostEditDialog
                    onClose={() => setShowAddKmPostDialog(false)}
                    onInsert={handleKmPostInsert}
                />
            )}
        </div>
    );
};
