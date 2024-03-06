import * as React from 'react';
import SwitchJointInfobox from 'tool-panel/switch/switch-joint-infobox';
import {
    suggestedSwitchJointsAsLayoutSwitchJointConnections,
    suggestedSwitchTopoLinksAsTopologicalJointConnections,
} from 'linking/linking-utils';
import { SuggestedSwitch } from 'linking/linking-model';
import { PublishType, SwitchStructure } from 'common/common-model';

type SwitchJointInfoboxContainerProps = {
    suggestedSwitch: SuggestedSwitch;
    suggestedSwitchStructure: SwitchStructure;
    publishType: PublishType;
};

export const SwitchJointInfoboxContainer: React.FC<SwitchJointInfoboxContainerProps> = ({
    suggestedSwitch,
    suggestedSwitchStructure,
    publishType,
}) => {
    const jointConnections = suggestedSwitchJointsAsLayoutSwitchJointConnections(suggestedSwitch);
    const switchAlignments = suggestedSwitchStructure.alignments;
    const topologicalJointConnections =
        suggestedSwitchTopoLinksAsTopologicalJointConnections(suggestedSwitch);

    return jointConnections ? (
        <SwitchJointInfobox
            switchAlignments={switchAlignments}
            jointConnections={jointConnections}
            topologicalJointConnections={topologicalJointConnections}
            publishType={publishType}
        />
    ) : (
        <React.Fragment />
    );
};
