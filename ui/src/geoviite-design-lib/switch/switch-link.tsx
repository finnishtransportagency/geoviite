import React from 'react';
import { LayoutSwitchId } from 'track-layout/track-layout-model';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { PublishType, TimeStamp } from 'common/common-model';
import { Link } from 'vayla-design-lib/link/link';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createEmptyItemCollections } from 'selection/selection-store';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { getSwitch } from 'track-layout/layout-switch-api';

export type SwitchLinkContainerProps = {
    switchId?: LayoutSwitchId;
};

export type SwitchLinkProps = {
    switchId?: LayoutSwitchId;
    publishType: PublishType;
    changeTime: TimeStamp;
    onClick?: (switchId: LayoutSwitchId) => void;
};

function createSelectAction() {
    const delegates = createDelegates(TrackLayoutActions);
    return (switchId: LayoutSwitchId) =>
        delegates.onSelect({
            ...createEmptyItemCollections(),
            switches: [switchId],
        });
}

export const SwitchLinkContainer: React.FC<SwitchLinkContainerProps> = (
    props: SwitchLinkContainerProps,
) => {
    const publicationType = useTrackLayoutAppSelector((state) => state.publishType);
    const changeTime = useCommonDataAppSelector((state) => state.changeTimes.layoutSwitch);
    return (
        <SwitchLink
            switchId={props.switchId}
            publishType={publicationType}
            changeTime={changeTime}
        />
    );
};

export const SwitchLink: React.FC<SwitchLinkProps> = (props: SwitchLinkProps) => {
    const [layoutSwitch, status] = useLoaderWithStatus(
        () =>
            props.switchId
                ? getSwitch(props.switchId, props.publishType, props.changeTime)
                : undefined,
        [props.switchId, props.publishType, props.changeTime],
    );
    const clickAction = props.onClick || createSelectAction();
    return status === LoaderStatus.Ready ? (
        <Link onClick={() => layoutSwitch && clickAction(layoutSwitch.id)}>
            {layoutSwitch?.name || ''}
        </Link>
    ) : (
        <Spinner />
    );
};
