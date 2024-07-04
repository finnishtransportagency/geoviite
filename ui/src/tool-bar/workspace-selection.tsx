import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import {
    getLayoutDesign,
    getLayoutDesigns,
    insertLayoutDesign,
    LayoutDesignSaveRequest,
    updateLayoutDesign,
} from 'track-layout/layout-design-api';
import { getChangeTimes, updateLayoutDesignChangeTime } from 'common/change-time-api';
import { WorkspaceDialog } from 'tool-bar/workspace-dialog';
import { WorkspaceDeleteConfirmDialog } from 'tool-bar/workspace-delete-confirm-dialog';
import { LayoutContext } from 'common/common-model';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { useTrackLayoutAppSelector, useUserHasPrivilege } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { EDIT_LAYOUT } from 'user/user-model';
import { PrivilegeRequired } from 'user/privilege-required';

type WorkspaceSelectionContainerProps = {
    setSelectingWorkspace: (selectingWorkspace: boolean) => void;
    selectingWorkspace: boolean;
};

export const WorkspaceSelectionContainer: React.FC<WorkspaceSelectionContainerProps> = ({
    setSelectingWorkspace,
    selectingWorkspace,
}) => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const delegates = React.useMemo(() => createDelegates(trackLayoutActionCreators), []);

    return (
        <WorkspaceSelection
            layoutContext={trackLayoutState.layoutContext}
            onLayoutContextChange={delegates.onLayoutContextChange}
            selectingWorkspace={selectingWorkspace}
            setSelectingWorkspace={setSelectingWorkspace}
        />
    );
};

type WorkspaceSelectionProps = {
    layoutContext: LayoutContext;
    onLayoutContextChange: (layoutContext: LayoutContext) => void;
    selectingWorkspace: boolean;
    setSelectingWorkspace: (selectingWorkspace: boolean) => void;
};

export const WorkspaceSelection: React.FC<WorkspaceSelectionProps> = ({
    layoutContext,
    onLayoutContextChange,
    selectingWorkspace,
    setSelectingWorkspace,
}) => {
    const { t } = useTranslation();

    const [showCreateWorkspaceDialog, setShowCreateWorkspaceDialog] = React.useState(false);
    const [showEditWorkspaceDialog, setShowEditWorkspaceDialog] = React.useState(false);
    const [showDeleteWorkspaceDialog, setShowDeleteWorkspaceDialog] = React.useState(false);

    const selectWorkspaceDropdownRef = React.useRef<HTMLInputElement>(null);

    React.useEffect(() => {
        if (selectingWorkspace) selectWorkspaceDropdownRef?.current?.focus();
    }, [selectingWorkspace]);

    const [designs, _] = useLoaderWithStatus(
        () => getLayoutDesigns(getChangeTimes().layoutDesign),
        [getChangeTimes().layoutDesign],
    );
    const [currentDesign, designLoadStatus] = useLoaderWithStatus(
        () =>
            layoutContext.designId &&
            getLayoutDesign(getChangeTimes().layoutDesign, layoutContext.designId),
        [getChangeTimes().layoutDesign, layoutContext.designId],
    );

    const selectedDesignDoesNotExist =
        designLoadStatus === LoaderStatus.Ready && currentDesign == undefined;
    if (selectedDesignDoesNotExist && !selectingWorkspace) {
        setSelectingWorkspace(true);
    }

    const canAddDesigns = useUserHasPrivilege(EDIT_LAYOUT);

    const unselectDesign = () => {
        onLayoutContextChange({
            publicationState: layoutContext.publicationState,
            designId: undefined,
        });
        setSelectingWorkspace(true);
    };

    const onAddClick = () => setShowCreateWorkspaceDialog(true);

    async function handleInsertLayoutDesign(request: LayoutDesignSaveRequest) {
        const designId = await insertLayoutDesign(request);
        await updateLayoutDesignChangeTime();
        onLayoutContextChange({ publicationState: 'DRAFT', designId: designId });
        setShowCreateWorkspaceDialog(false);
        setSelectingWorkspace(false);
    }

    return (
        <React.Fragment>
            <Dropdown
                inputRef={selectWorkspaceDropdownRef}
                placeholder={t('tool-bar.choose-workspace')}
                openOverride={selectingWorkspace || undefined}
                onAddClick={canAddDesigns ? onAddClick : undefined}
                onChange={(designId) => {
                    setSelectingWorkspace(false);
                    onLayoutContextChange({
                        publicationState: 'DRAFT',
                        designId: designId,
                    });
                }}
                options={
                    designs?.map((design) => ({
                        value: design.id,
                        name: design.name,
                        qaId: `workspace-${design.id}`,
                    })) ?? []
                }
                value={layoutContext.designId}
                qaId={'workspace-selection'}
            />
            <PrivilegeRequired privilege={EDIT_LAYOUT}>
                <Button
                    variant={ButtonVariant.GHOST}
                    icon={Icons.Edit}
                    disabled={!layoutContext.designId}
                    onClick={() => setShowEditWorkspaceDialog(true)}
                    qa-id={'workspace-edit-button'}
                />
                <Button
                    variant={ButtonVariant.GHOST}
                    icon={Icons.Delete}
                    disabled={!layoutContext.designId}
                    onClick={() => setShowDeleteWorkspaceDialog(true)}
                    qa-id={'workspace-delete-button'}
                />
            </PrivilegeRequired>
            {showCreateWorkspaceDialog && (
                <WorkspaceDialog
                    onCancel={() => {
                        setShowCreateWorkspaceDialog(false);
                        setSelectingWorkspace(false);
                    }}
                    onSave={(_, request) => handleInsertLayoutDesign(request)}
                />
            )}

            {showEditWorkspaceDialog && (
                <WorkspaceDialog
                    existingDesign={currentDesign}
                    onCancel={() => {
                        setShowEditWorkspaceDialog(false);
                        setSelectingWorkspace(false);
                    }}
                    onSave={(_, request) => {
                        if (currentDesign) {
                            updateLayoutDesign(currentDesign.id, request).finally(() => {
                                updateLayoutDesignChangeTime();
                                setShowEditWorkspaceDialog(false);
                            });
                        }
                    }}
                />
            )}

            {showDeleteWorkspaceDialog && currentDesign && (
                <WorkspaceDeleteConfirmDialog
                    closeDialog={() => setShowDeleteWorkspaceDialog(false)}
                    currentDesign={currentDesign}
                    onDesignDeleted={unselectDesign}
                />
            )}
        </React.Fragment>
    );
};
