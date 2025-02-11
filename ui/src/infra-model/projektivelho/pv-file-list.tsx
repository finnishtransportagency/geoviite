import * as React from 'react';
import { useState } from 'react';
import { Table, Th } from 'vayla-design-lib/table/table';
import { Trans, useTranslation } from 'react-i18next';
import styles from './pv-file-list.scss';
import { AccordionToggle } from 'vayla-design-lib/accordion-toggle/accordion-toggle';
import { formatDateFull } from 'utils/date-utils';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { PVDocumentHeader, PVDocumentId } from './pv-model';
import { useAppNavigate } from 'common/navigate';
import {
    getPVDocuments,
    projektivelhoDocumentDownloadUri,
    rejectPVDocuments,
    restorePVDocuments,
} from 'infra-model/infra-model-api';
import { updatePVDocumentsChangeTime } from 'common/change-time-api';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { Oid, TimeStamp } from 'common/common-model';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import {
    getPVSortInfoForProp,
    PVTableSortField,
    PVTableSortInformation,
    sortPVTableColumns,
} from 'infra-model/projektivelho/pv-file-list-utils';
import { getSortDirectionIcon, SortDirection } from 'utils/table-utils';
import { Menu, menuOption, MenuSelectOption } from 'vayla-design-lib/menu/menu';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { PVRedirectLink } from 'infra-model/projektivelho/pv-redirect-link';
import { PrivilegedLink } from 'user/privileged-link';
import { DOWNLOAD_GEOMETRY, EDIT_GEOMETRY_FILE } from 'user/user-model';
import { PrivilegeRequired } from 'user/privilege-required';

type ListMode = 'SUGGESTED' | 'REJECTED';

type PVFileListContainerProps = {
    changeTime: TimeStamp;
    listMode: ListMode;
    sortPersistorFn: (sortInfo: PVTableSortInformation) => void;
    sorting: PVTableSortInformation;
};

type PVFileListProps = {
    documentHeaders: PVDocumentHeader[];
    isLoading: boolean;
    listMode: ListMode;
    onReject: (ids: PVDocumentId[]) => void;
    onImport: (id: PVDocumentId) => void;
    onRestore: (id: PVDocumentId[]) => void;
    onSort: (sortInfo: PVTableSortInformation) => void;
    sorting: PVTableSortInformation;
    changeTime: TimeStamp;
    documentsReloading: PVDocumentId[];
};

export const PVFileListContainer: React.FC<PVFileListContainerProps> = ({
    changeTime,
    listMode,
    sortPersistorFn,
    sorting,
}: PVFileListContainerProps) => {
    const navigate = useAppNavigate();
    const [loadingForUserTriggeredChange, setLoadingForUserTriggeredChange] = useState(false);
    const [documentsReloading, setDocumentsReloading] = React.useState<PVDocumentId[]>([]);

    const [documentHeaders, isLoading] = useLoaderWithStatus(async () => {
        const documents = await getPVDocuments(changeTime, listMode);
        setLoadingForUserTriggeredChange(false);
        setDocumentsReloading([]);
        return documents;
    }, [changeTime]);

    return (
        <div className="projektivelho-file-list">
            <PVFileList
                documentsReloading={documentsReloading}
                documentHeaders={documentHeaders || []}
                isLoading={isLoading !== LoaderStatus.Ready && !loadingForUserTriggeredChange}
                onReject={(ids) => {
                    setDocumentsReloading([...documentsReloading, ...ids]);

                    rejectPVDocuments(ids).then(() => {
                        setLoadingForUserTriggeredChange(true);
                        updatePVDocumentsChangeTime();
                    });
                }}
                onImport={(id) => navigate('inframodel-import', id)}
                onRestore={(ids) => {
                    setDocumentsReloading([...documentsReloading, ...ids]);

                    restorePVDocuments(ids).then(() => {
                        setLoadingForUserTriggeredChange(true);
                        updatePVDocumentsChangeTime();
                    });
                }}
                onSort={sortPersistorFn}
                sorting={sorting}
                listMode={listMode}
                changeTime={changeTime}
            />
        </div>
    );
};

export const PVFileList = ({
    documentHeaders,
    isLoading,
    onReject,
    onImport,
    onRestore,
    listMode,
    changeTime,
    documentsReloading,
    sorting,
    onSort,
}: PVFileListProps) => {
    const { t } = useTranslation();

    const filter = (filter: (item: PVDocumentHeader) => unknown) =>
        documentHeaders.filter(filter).map((item) => item.document.id);

    const filterByAssignment = (assignmentOid: Oid | undefined) =>
        filter((header) => header.assignment?.oid && header.assignment.oid === assignmentOid);

    const filterByProject = (projectOid: Oid | undefined) =>
        filter((header) => header.project && header.project.oid === projectOid);

    const filterByProjectGroup = (projectGroupOid: Oid | undefined) =>
        filter((header) => header.projectGroup && header.projectGroup.oid === projectGroupOid);

    const rejectByAssignment = (assignmentOid: Oid) => onReject(filterByAssignment(assignmentOid));
    const rejectByProject = (projectOid: Oid) => onReject(filterByProject(projectOid));
    const rejectByProjectGroup = (projectGroupOid: Oid) =>
        onReject(filterByProjectGroup(projectGroupOid));

    const restoreByAssignment = (assigmentOid: Oid) => onRestore(filterByAssignment(assigmentOid));
    const restoreByProject = (projectOid: Oid) => onRestore(filterByProject(projectOid));
    const restoreByProjectGroup = (projectGroupOid: Oid) =>
        onRestore(filterByProjectGroup(projectGroupOid));

    const [sortedDocumentHeaders, setSortedDocumentHeaders] = React.useState<PVDocumentHeader[]>([
        ...documentHeaders,
    ]);

    React.useEffect(() => {
        const sortableDocumentHeaders = sortPVTableColumns(sorting, [...documentHeaders]);
        setSortedDocumentHeaders(sortableDocumentHeaders);
    }, [documentHeaders, sorting]);

    const sortByProp = (propName: PVTableSortField) => {
        const newSortInfo = getPVSortInfoForProp(sorting.direction, sorting.propName, propName);

        if (newSortInfo.direction === SortDirection.UNSORTED) {
            setSortedDocumentHeaders([...documentHeaders]);
        }
        onSort(newSortInfo);
    };

    const sortableTableHeader = (prop: PVTableSortField, translationKey: string, qaId: string) => (
        <Th
            onClick={() => sortByProp(prop)}
            qa-id={qaId}
            icon={sorting?.propName === prop ? getSortDirectionIcon(sorting.direction) : undefined}>
            {t(translationKey)}
        </Th>
    );

    return (
        <Table className={styles['projektivelho-file-list__table']} wide isLoading={isLoading}>
            <thead>
                <tr>
                    <Th></Th>
                    {sortableTableHeader(
                        PVTableSortField.PROJECT_NAME,
                        'projektivelho.file-list.header.project-name',
                        'projektivelho.project-name',
                    )}
                    {sortableTableHeader(
                        PVTableSortField.DOCUMENT_NAME,
                        'projektivelho.file-list.header.document-name',
                        'projektivelho.document-name',
                    )}
                    {sortableTableHeader(
                        PVTableSortField.DOCUMENT_DESCRIPTION,
                        'projektivelho.file-list.header.document-description',
                        'projektivelho.document-description',
                    )}
                    {sortableTableHeader(
                        PVTableSortField.DOCUMENT_MODIFIED,
                        'projektivelho.file-list.header.document-modified',
                        'projektivelho.document-modified',
                    )}
                    <PrivilegeRequired privilege={EDIT_GEOMETRY_FILE}>
                        <Th></Th>
                    </PrivilegeRequired>
                </tr>
            </thead>
            <tbody>
                {sortedDocumentHeaders.map((item) => (
                    <PVFileListRow
                        actionsDisabled={documentsReloading.includes(item.document.id)}
                        listMode={listMode}
                        key={item.document.id}
                        item={item}
                        onReject={() => onReject([item.document.id])}
                        onImport={() => onImport(item.document.id)}
                        onRestore={() => onRestore([item.document.id])}
                        onRestoreByProject={(oid: Oid) => restoreByProject(oid)}
                        onRestoreByAssignment={(oid: Oid) => restoreByAssignment(oid)}
                        onRestoreByProjectGroup={(oid: Oid) => restoreByProjectGroup(oid)}
                        onRejectByProject={(oid: Oid) => rejectByProject(oid)}
                        onRejectByProjectGroup={(oid: Oid) => rejectByProjectGroup(oid)}
                        onRejectByAssignment={(oid: Oid) => rejectByAssignment(oid)}
                        changeTime={changeTime}
                        itemCounts={{
                            assignment: filterByAssignment(item.assignment?.oid).length,
                            project: filterByProject(item.project?.oid).length,
                            projectGroup: filterByProjectGroup(item.projectGroup?.oid).length,
                        }}
                    />
                ))}
            </tbody>
        </Table>
    );
};

type PVFileListRowProps = {
    item: PVDocumentHeader;
    listMode: ListMode;
    onReject: () => void;
    onImport: () => void;
    onRestore: () => void;
    changeTime: TimeStamp;
    onRejectByProject: (oid: Oid) => void;
    onRejectByProjectGroup: (oid: Oid) => void;
    onRejectByAssignment: (oid: Oid) => void;
    onRestoreByProject: (oid: Oid) => void;
    onRestoreByProjectGroup: (oid: Oid) => void;
    onRestoreByAssignment: (oid: Oid) => void;
    actionsDisabled: boolean;
    itemCounts: { project: number; projectGroup: number; assignment: number };
};

const PVFileListRow = ({
    item,
    listMode,
    onReject,
    onImport,
    onRestore,
    changeTime,
    onRejectByProject,
    onRejectByProjectGroup,
    onRejectByAssignment,
    onRestoreByProject,
    onRestoreByProjectGroup,
    onRestoreByAssignment,
    itemCounts,
    actionsDisabled,
}: PVFileListRowProps) => {
    const { t } = useTranslation(undefined, { keyPrefix: 'projektivelho.file-list' });

    const [isOpen, setIsOpen] = React.useState(false);
    const [fileActionMenuVisible, setFileActionMenuVisible] = React.useState(false);
    const [showConfirmDialog, setShowConfirmDialog] = React.useState(false);
    const dialogParams = React.useRef<{
        title: string;
        message: { key: string; params: Record<string, string | number | undefined> };
        onConfirm: () => void;
        confirmText: string;
    }>(undefined);

    const actionMenuRef = React.useRef(null);
    const suggestedList = listMode === 'SUGGESTED';

    const dialogTranslationPrefix = `confirm-dialog.${suggestedList ? 'reject' : 'restore'}`;

    const fileActions: MenuSelectOption[] = [
        menuOption(
            () => {
                dialogParams.current = {
                    title: t(`${dialogTranslationPrefix}.by-assignment-title`),
                    message: {
                        key: `${dialogTranslationPrefix}.by-assignment-message`,
                        params: {
                            assignment: item.assignment?.name,
                            assignmentCount: itemCounts.assignment,
                        },
                    },
                    onConfirm: () => {
                        item.assignment &&
                            (suggestedList ? onRejectByAssignment : onRestoreByAssignment)(
                                item.assignment?.oid,
                            );
                    },
                    confirmText: t(`${dialogTranslationPrefix}.confirm`, {
                        count: itemCounts.assignment,
                    }),
                };

                setShowConfirmDialog(true);
            },
            t(`${dialogTranslationPrefix}.by-assignment`, {
                assignmentCount: itemCounts.assignment ?? '-',
            }),
            'pv-reject-or-restore-by-assignment',
            !item.assignment?.oid,
        ),
        menuOption(
            () => {
                dialogParams.current = {
                    title: t(`${dialogTranslationPrefix}.by-project-title`),
                    message: {
                        key: `${dialogTranslationPrefix}.by-project-message`,
                        params: {
                            projectName: item.project?.name,
                            projectCount: itemCounts.project,
                        },
                    },
                    onConfirm: () => {
                        item.project &&
                            (suggestedList ? onRejectByProject : onRestoreByProject)(
                                item.project?.oid,
                            );
                    },
                    confirmText: t(`${dialogTranslationPrefix}.confirm`, {
                        count: itemCounts.project,
                    }),
                };

                setShowConfirmDialog(true);
            },
            t(`${dialogTranslationPrefix}.by-project`, {
                projectCount: itemCounts.project ?? '-',
            }),
            'pv-reject-or-restore-by-project',
            !item.project?.oid,
        ),
        menuOption(
            () => {
                dialogParams.current = {
                    title: t(`${dialogTranslationPrefix}.by-project-group-title`),
                    message: {
                        key: `${dialogTranslationPrefix}.by-project-group-message`,
                        params: {
                            projectGroup: item.projectGroup?.name,
                            groupCount: itemCounts.projectGroup,
                        },
                    },
                    onConfirm: () => {
                        item.projectGroup &&
                            (suggestedList ? onRejectByProjectGroup : onRestoreByProjectGroup)(
                                item.projectGroup?.oid,
                            );
                    },
                    confirmText: t(`${dialogTranslationPrefix}.confirm`, {
                        count: itemCounts.projectGroup,
                    }),
                };

                setShowConfirmDialog(true);
            },
            t(`${dialogTranslationPrefix}.by-project-group`, {
                groupCount: itemCounts.projectGroup ?? '-',
            }),
            'pv-reject-or-restore-by-project-group',
            !item.projectGroup?.oid,
        ),
    ];

    const confirmDialog = () => {
        return (
            <Dialog
                variant={DialogVariant.LIGHT}
                title={dialogParams.current?.title}
                onClose={() => setShowConfirmDialog(false)}
                footerContent={
                    <div className={dialogStyles['dialog__footer-content--centered']}>
                        <Button
                            variant={ButtonVariant.SECONDARY}
                            onClick={() => setShowConfirmDialog(false)}>
                            {t('cancel')}
                        </Button>
                        <Button
                            onClick={() => {
                                setShowConfirmDialog(false);
                                dialogParams.current?.onConfirm();
                            }}>
                            {dialogParams.current?.confirmText}
                        </Button>
                    </div>
                }>
                <Trans
                    t={t}
                    i18nKey={dialogParams.current?.message.key}
                    values={dialogParams.current?.message.params}
                />
            </Dialog>
        );
    };

    return (
        <>
            <tr key={`${item.document.id}`}>
                <td>
                    <AccordionToggle open={isOpen} onToggle={() => setIsOpen(!isOpen)} />
                </td>
                <td>{item.project && item.project.name}</td>
                <td>
                    <PrivilegedLink
                        privilege={DOWNLOAD_GEOMETRY}
                        className={styles['projektivelho-file-list__link']}
                        href={projektivelhoDocumentDownloadUri(item.document.id)}>
                        {item.document.name}
                    </PrivilegedLink>
                </td>
                <td>{item.document.description}</td>
                <td>{formatDateFull(item.document.modified)}</td>
                <PrivilegeRequired privilege={EDIT_GEOMETRY_FILE}>
                    <td>
                        <div
                            className={styles['projektivelho-file-list__buttons']}
                            ref={actionMenuRef}>
                            {suggestedList && (
                                <Button
                                    disabled={actionsDisabled}
                                    title={t('reject-tooltip')}
                                    variant={ButtonVariant.SECONDARY}
                                    onClick={onReject}
                                    qa-id="pv-reject-button">
                                    {t('reject')}
                                </Button>
                            )}
                            {!suggestedList && (
                                <Button
                                    disabled={actionsDisabled}
                                    title={t('restore-tooltip')}
                                    variant={ButtonVariant.SECONDARY}
                                    onClick={onRestore}
                                    qa-id="pv-restore-button">
                                    {t('restore')}
                                </Button>
                            )}
                            <Button
                                title={t('upload-tooltip')}
                                variant={ButtonVariant.SECONDARY}
                                disabled={actionsDisabled}
                                onClick={onImport}
                                qa-id="pv-import-button">
                                {t('upload')}
                            </Button>
                            <Button
                                title={t('more')}
                                variant={ButtonVariant.SECONDARY}
                                disabled={actionsDisabled}
                                onClick={() => {
                                    setFileActionMenuVisible(!fileActionMenuVisible);
                                }}
                                qa-id="pv-menu-button">
                                {'...'}
                            </Button>
                        </div>
                        {fileActionMenuVisible && (
                            <Menu
                                anchorElementRef={actionMenuRef}
                                items={fileActions}
                                onClickOutside={() => setFileActionMenuVisible(false)}
                                onClose={() => setFileActionMenuVisible(false)}
                            />
                        )}
                        {showConfirmDialog && confirmDialog()}
                    </td>
                </PrivilegeRequired>
            </tr>
            {isOpen ? (
                <tr>
                    <td></td>
                    <td colSpan={5}>
                        <PVFileListExpandedItem item={item} changeTime={changeTime} />
                    </td>
                </tr>
            ) : (
                <></>
            )}
        </>
    );
};

type PVFileListExpandedItemProps = {
    item: PVDocumentHeader;
    changeTime: TimeStamp;
};

const PVFileListExpandedItem = ({ item }: PVFileListExpandedItemProps) => {
    const { t } = useTranslation(undefined, { keyPrefix: 'projektivelho.file-list.field' });
    return (
        <div className={styles['projektivelho-file-list__expanded']}>
            <InfoboxContent>
                {item.projectGroup && (
                    <InfoboxField
                        label={t('project-group')}
                        value={
                            <PVRedirectLink projectGroupOid={item.projectGroup.oid}>
                                {`${item.projectGroup.name} (${item.projectGroup.state})`}
                            </PVRedirectLink>
                        }
                    />
                )}
                {item.project && (
                    <InfoboxField
                        label={t('project-name')}
                        value={
                            <PVRedirectLink projectOid={item.project.oid}>
                                {`${item.project.name} (${item.project.state})`}
                            </PVRedirectLink>
                        }
                    />
                )}
                {item.assignment && item.project && (
                    <InfoboxField
                        label={t('assignment')}
                        value={
                            <PVRedirectLink
                                assignmentOid={item.assignment.oid}
                                projectOid={item.project.oid}>
                                {`${item.assignment.name} (${item.assignment.state})`}
                            </PVRedirectLink>
                        }
                    />
                )}
            </InfoboxContent>
            <InfoboxContent>
                <InfoboxField
                    label={t('material-group')}
                    value={`${item.document.group} / ${item.document.category}`}
                />
                <InfoboxField label={t('document-type')} value={item.document.type} />
                <InfoboxField label={t('document-state')} value={item.document.state} />
            </InfoboxContent>
        </div>
    );
};
