import * as React from 'react';
import SwitchJointInfobox from 'tool-panel/switch/switch-joint-infobox';
import { asTrackLayoutSwitchJointConnection } from 'linking/linking-utils';
import { SuggestedSwitch } from 'linking/linking-model';
import { PublishType } from 'common/common-model';

type SwitchJointInfoboxContainerProps = {
    suggestedSwitch: SuggestedSwitch;
    publishType: PublishType;
};

export const SwitchJointInfoboxContainer: React.FC<SwitchJointInfoboxContainerProps> = ({
    suggestedSwitch,
    publishType,
}) => {
    const jointConnections = suggestedSwitch
        ? suggestedSwitch.joints.map((joint) => asTrackLayoutSwitchJointConnection(joint))
        : undefined;
    const switchAlignments = suggestedSwitch.switchStructure.alignments;

    return jointConnections ? (
        <SwitchJointInfobox
            switchAlignments={switchAlignments}
            jointConnections={jointConnections}
            topologicalJointConnections={suggestedSwitch?.topologicalJointConnections}
            publishType={publishType}
        />
    ) : (
        <React.Fragment />
    );
};
