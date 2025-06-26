import * as React from 'react';
import {
    LayoutAssetFields,
    LayoutKmPost,
    LayoutLocationTrack,
    LayoutState,
    LayoutSwitch,
    LocationTrackNamingScheme,
} from 'track-layout/track-layout-model';
import { KmPostBadge, KmPostBadgeStatus } from 'geoviite-design-lib/km-post/km-post-badge';
import { SwitchBadge, SwitchBadgeStatus } from 'geoviite-design-lib/switch/switch-badge';
import {
    LocationTrackBadge,
    LocationTrackBadgeStatus,
} from 'geoviite-design-lib/alignment/location-track-badge';
import { brand } from 'common/brand';

const layoutAssetFields: LayoutAssetFields = {
    version: 'version',
    dataType: 'TEMP',
    isDraft: true,
};

const layoutLocationTrack: LayoutLocationTrack = {
    ...layoutAssetFields,
    id: brand(''),
    nameStructure: {
        scheme: LocationTrackNamingScheme.FREE_TEXT,
        freeText: 'name',
    },
    name: 'name',
    descriptionStructure: {
        base: 'description',
        suffix: 'NONE',
    },
    description: 'description',
    state: 'IN_USE' as LayoutState,
    length: 123,
    segmentCount: 0,
    boundingBox: { x: { min: 0, max: 0 }, y: { min: 0, max: 0 } },
    trackNumberId: brand(''),
    sourceId: brand(''),
    type: 'MAIN',
    duplicateOf: undefined,
    topologicalConnectivity: 'NONE',
    ownerId: '',
};
const kmPost: LayoutKmPost = {
    ...layoutAssetFields,
    id: brand(''),
    kmNumber: '123',
    layoutLocation: { x: 0, y: 0 },
    state: 'IN_USE' as LayoutState,
    trackNumberId: brand(''),
    gkLocation: undefined,
};
const layoutSwitch: LayoutSwitch = {
    ...layoutAssetFields,
    id: brand(''),
    name: 'V1234',
    switchStructureId: '',
    stateCategory: 'EXISTING',
    joints: [],
};

export const BadgeExamples: React.FC = () => {
    return (
        <div>
            <h2>Badges</h2>
            <table>
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>Default</th>
                        <th>Linked</th>
                        <th>Unlinked</th>
                        <th>Selected</th>
                        <th>Disabled</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>Location tracks</td>
                        <td>
                            <LocationTrackBadge locationTrack={layoutLocationTrack} />
                        </td>
                        <td>
                            <LocationTrackBadge
                                locationTrack={layoutLocationTrack}
                                status={LocationTrackBadgeStatus.LINKED}
                            />
                        </td>
                        <td>
                            <LocationTrackBadge
                                locationTrack={layoutLocationTrack}
                                status={LocationTrackBadgeStatus.UNLINKED}
                            />
                        </td>
                        <td>
                            <LocationTrackBadge
                                locationTrack={layoutLocationTrack}
                                status={LocationTrackBadgeStatus.SELECTED}
                            />
                        </td>
                        <td>
                            <LocationTrackBadge
                                locationTrack={layoutLocationTrack}
                                status={LocationTrackBadgeStatus.DISABLED}
                            />
                        </td>
                    </tr>
                    <tr>
                        <td>Switches</td>
                        <td>
                            <SwitchBadge switchItem={layoutSwitch} />
                        </td>
                        <td>
                            <SwitchBadge
                                switchItem={layoutSwitch}
                                status={SwitchBadgeStatus.LINKED}
                            />
                        </td>
                        <td>
                            <SwitchBadge
                                switchItem={layoutSwitch}
                                status={SwitchBadgeStatus.UNLINKED}
                            />
                        </td>
                        <td>
                            <SwitchBadge
                                switchItem={layoutSwitch}
                                status={SwitchBadgeStatus.SELECTED}
                            />
                        </td>
                        <td>
                            <SwitchBadge
                                switchItem={layoutSwitch}
                                status={SwitchBadgeStatus.DISABLED}
                            />
                        </td>
                    </tr>
                    <tr>
                        <td>KM-Posts</td>
                        <td>
                            <KmPostBadge kmPost={kmPost} />
                        </td>
                        <td>
                            <KmPostBadge kmPost={kmPost} status={KmPostBadgeStatus.LINKED} />
                        </td>
                        <td>
                            <KmPostBadge kmPost={kmPost} status={KmPostBadgeStatus.UNLINKED} />
                        </td>
                        <td>
                            <KmPostBadge kmPost={kmPost} status={KmPostBadgeStatus.SELECTED} />
                        </td>
                        <td>
                            <KmPostBadge kmPost={kmPost} status={KmPostBadgeStatus.DISABLED} />
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    );
};
