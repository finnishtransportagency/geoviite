import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { useLoader } from 'utils/react-utils';
import {
    getLayoutDesigns,
    insertLayoutDesign,
    updateLayoutDesign,
} from 'track-layout/layout-design-api';
import { getChangeTimes, updateLayoutDesignChangeTime } from 'common/change-time-api';
import { WorkspaceDialog } from 'tool-bar/workspace-dialog';
import { WorkspaceDeleteConfirmDialog } from 'tool-bar/workspace-delete-confirm-dialog';
import { LayoutContext } from 'common/common-model';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';

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

    const designs = useLoader(
        () => getLayoutDesigns(getChangeTimes().layoutDesign),
        [getChangeTimes().layoutDesign],
    );
    const currentDesign = designs?.find((d) => d.id === layoutContext.designId);

    const unselectDesign = () => {
        onLayoutContextChange({
            publicationState: layoutContext.publicationState,
            designId: undefined,
        });
        setSelectingWorkspace(true);
    };

    return (
        <React.Fragment>
            <Dropdown
                inputRef={selectWorkspaceDropdownRef}
                placeholder={t('tool-bar.choose-workspace')}
                openOverride={selectingWorkspace || undefined}
                onAddClick={() => setShowCreateWorkspaceDialog(true)}
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
            />
            <Button
                variant={ButtonVariant.GHOST}
                icon={Icons.Edit}
                disabled={!layoutContext.designId}
                onClick={() => setShowEditWorkspaceDialog(true)}
            />
            <Button
                variant={ButtonVariant.GHOST}
                icon={Icons.Delete}
                disabled={!layoutContext.designId}
                onClick={() => setShowDeleteWorkspaceDialog(true)}
            />
            {showCreateWorkspaceDialog && (
                <WorkspaceDialog
                    onCancel={() => {
                        setShowCreateWorkspaceDialog(false);
                        setSelectingWorkspace(false);
                    }}
                    onSave={(_, request) => {
                        insertLayoutDesign(request)
                            .then((id) => {
                                setSelectingWorkspace(false);
                                onLayoutContextChange({ publicationState: 'DRAFT', designId: id });
                            })
                            .finally(() => {
                                updateLayoutDesignChangeTime();
                                setShowCreateWorkspaceDialog(false);
                            });
                    }}
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
