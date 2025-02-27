import React from 'react';
import { LayoutSwitchId } from 'track-layout/track-layout-model';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { LayoutContext, TimeStamp } from 'common/common-model';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createEmptyItemCollections } from 'selection/selection-store';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { getSwitch } from 'track-layout/layout-switch-api';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';

export type SwitchLinkContainerProps = {
    switchId?: LayoutSwitchId;
};

export type SwitchLinkProps = {
    switchId?: LayoutSwitchId;
    layoutContext: LayoutContext;
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
    const layoutContext = useTrackLayoutAppSelector((state) => state.layoutContext);
    const changeTime = useCommonDataAppSelector((state) => state.changeTimes.layoutSwitch);
    return (
        <SwitchLink
            switchId={props.switchId}
            layoutContext={layoutContext}
            changeTime={changeTime}
        />
    );
};

export const SwitchLink: React.FC<SwitchLinkProps> = (props: SwitchLinkProps) => {
    const [layoutSwitch, status] = useLoaderWithStatus(
        () =>
            props.switchId
                ? getSwitch(props.switchId, props.layoutContext, props.changeTime)
                : undefined,
        [
            props.switchId,
            props.layoutContext.publicationState,
            props.layoutContext.branch,
            props.changeTime,
        ],
    );
    const clickAction = props.onClick || createSelectAction();
    return status === LoaderStatus.Ready ? (
        <AnchorLink onClick={() => layoutSwitch && clickAction(layoutSwitch.id)}>
            {layoutSwitch?.name || ''}
        </AnchorLink>
    ) : (
        <Spinner />
    );
};
