import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dropdown, DropdownPopupMode, nameIncludes } from 'vayla-design-lib/dropdown/dropdown';
import { useLoaderWithStatus } from 'utils/react-utils';
import {
    getLayoutDesigns,
    insertLayoutDesign,
    LayoutDesignSaveRequest,
} from 'track-layout/layout-design-api';
import { getChangeTimes, updateLayoutDesignChangeTime } from 'common/change-time-api';
import { WorkspaceDialog } from 'tool-bar/workspace-dialog';
import { LayoutDesignId } from 'common/common-model';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { useTrackLayoutAppSelector, useUserHasPrivilege } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { EDIT_LAYOUT } from 'user/user-model';

type DesignSelectionContainerProps = {
    onDesignIdChange: (value: LayoutDesignId) => void;
};

export const DesignSelectionContainer: React.FC<DesignSelectionContainerProps> = ({
    onDesignIdChange,
}) => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const delegates = React.useMemo(() => createDelegates(trackLayoutActionCreators), []);

    function onDesignIdChangeLocal(designId: LayoutDesignId) {
        delegates.onDesignIdChange(designId);
        onDesignIdChange(designId);
    }

    return (
        <DesignSelection
            onDesignSelected={onDesignIdChangeLocal}
            designId={trackLayoutState.designId}
        />
    );
};

type DesignSelectionProps = {
    designId: LayoutDesignId | undefined;
    onDesignSelected: (value: LayoutDesignId) => void;
};

export const DesignSelection: React.FC<DesignSelectionProps> = ({ designId, onDesignSelected }) => {
    const { t } = useTranslation();
    const [showCreateWorkspaceDialog, setShowCreateWorkspaceDialog] = React.useState(false);
    const [savingWorkspace, setSavingWorkspace] = React.useState(false);
    const selectWorkspaceDropdownRef = React.useRef<HTMLInputElement>(null);

    React.useEffect(() => {
        setTimeout(() => selectWorkspaceDropdownRef?.current?.focus(), 0);
    });

    const [designs, _] = useLoaderWithStatus(
        () => getLayoutDesigns(getChangeTimes().layoutDesign),
        [getChangeTimes().layoutDesign],
    );

    const canAddDesigns = useUserHasPrivilege(EDIT_LAYOUT);

    const onAddClick = () => setShowCreateWorkspaceDialog(true);

    async function handleInsertLayoutDesign(request: LayoutDesignSaveRequest) {
        const designId = await insertLayoutDesign(request);
        await updateLayoutDesignChangeTime();
        onDesignSelected(designId);
    }

    return (
        <React.Fragment>
            {!showCreateWorkspaceDialog && (
                <Dropdown
                    inputRef={selectWorkspaceDropdownRef}
                    placeholder={t('tool-bar.search-design')}
                    filter={nameIncludes}
                    displaySelectedName={false}
                    openOverride={true}
                    popupMode={DropdownPopupMode.Inline}
                    onAddClick={canAddDesigns ? onAddClick : undefined}
                    onChange={(designId) => designId && onDesignSelected(designId)}
                    options={
                        designs
                            ?.map((design) => ({
                                value: design.id,
                                name: design.name,
                                qaId: `workspace-${design.id}`,
                            }))
                            .toSorted((a, b) => a.name.localeCompare(b.name)) ?? []
                    }
                    value={designId}
                    qaId={'workspace-selection'}
                    customIcon={Icons.Search}
                />
            )}

            {showCreateWorkspaceDialog && (
                <WorkspaceDialog
                    onCancel={() => setShowCreateWorkspaceDialog(false)}
                    onSave={(_, request) => {
                        setSavingWorkspace(true);
                        handleInsertLayoutDesign(request).finally(() => setSavingWorkspace(false));
                    }}
                    saving={savingWorkspace}
                />
            )}
        </React.Fragment>
    );
};
