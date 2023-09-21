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
    restorePVDocument,
} from 'infra-model/infra-model-api';
import { updatePVDocumentsChangeTime } from 'common/change-time-api';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { Oid, TimeStamp } from 'common/common-model';
import { Link } from 'vayla-design-lib/link/link';
//import { PVRedirectLink } from 'infra-model/projektivelho/pv-redirect-link';
import { WriteAccessRequired } from 'user/write-access-required';
import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import {
    getPVSortInfoForProp,
    PVInitiallyUnsorted,
    PVTableSortField,
    PVTableSortInformation,
    sortPVTableColumns,
} from 'infra-model/projektivelho/pv-file-list-utils';
import { getSortDirectionIcon, SortDirection } from 'utils/table-utils';
import { Menu } from 'vayla-design-lib/menu/menu';

type ListMode = 'SUGGESTED' | 'REJECTED';

type PVFileListContainerProps = {
    changeTime: TimeStamp;
    listMode: ListMode;
};

type PVFileListProps = {
    documentHeaders: PVDocumentHeader[];
    isLoading: boolean;
    listMode: ListMode;
    onReject: (ids: PVDocumentId[]) => void;
    onImport: (id: PVDocumentId) => void;
    onRestore: (id: PVDocumentId) => void;
    changeTime: TimeStamp;
};

export const PVFileListContainer: React.FC<PVFileListContainerProps> = ({
    changeTime,
    listMode,
}: PVFileListContainerProps) => {
    const navigate = useAppNavigate();
    const [documentHeaders, isLoading] =
        useLoaderWithStatus(async () => {
            const documents = await getPVDocuments(changeTime, listMode);
            setLoadingForUserTriggeredChange(false);
            return documents;
        }, [changeTime]) || [];
    const [loadingForUserTriggeredChange, setLoadingForUserTriggeredChange] = useState(false);

    return (
        <div className="projektivelho-file-list">
            <PVFileList
                documentHeaders={documentHeaders || []}
                isLoading={isLoading !== LoaderStatus.Ready && !loadingForUserTriggeredChange}
                onReject={(ids) =>
                    rejectPVDocuments(ids).then(() => {
                        setLoadingForUserTriggeredChange(true);
                        updatePVDocumentsChangeTime();
                    })
                }
                onImport={(id) => navigate('inframodel-import', id)}
                onRestore={(id) =>
                    restorePVDocument(id).then(() => {
                        setLoadingForUserTriggeredChange(true);
                        updatePVDocumentsChangeTime();
                    })
                }
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
}: PVFileListProps) => {
    const { t } = useTranslation();

    const rejectByFilter = (filter: (item: PVDocumentHeader) => boolean) => {
        onReject(documentHeaders.filter((item) => filter(item)).map((item) => item.document.id));
    };

    const isAssignment = (header: PVDocumentHeader, assignmentOid: string | undefined) =>
        header.assignment?.oid === assignmentOid;
    const isProject = (header: PVDocumentHeader, projectOid: string | undefined) =>
        header.project?.oid === projectOid;
    const isProjectGroup = (header: PVDocumentHeader, projectGroupOid: string | undefined) =>
        header.projectGroup?.oid === projectGroupOid;

    const rejectByAssignment = (assignmentOid: string) =>
        rejectByFilter((item: PVDocumentHeader) => isAssignment(item, assignmentOid));
    const rejectByProject = (projectOid: string) =>
        rejectByFilter((item: PVDocumentHeader) => isProject(item, projectOid));
    const rejectByProjectGroup = (projectGroupOid: string) =>
        rejectByFilter((item: PVDocumentHeader) => isProjectGroup(item, projectGroupOid));

    const [sortInfo, setSortInfo] = React.useState<PVTableSortInformation>(PVInitiallyUnsorted);

    const [sortedDocumentHeaders, setSortedDocumentHeaders] = React.useState<PVDocumentHeader[]>([
        ...documentHeaders,
    ]);

    React.useEffect(() => {
        const sortableDocumentHeaders = sortPVTableColumns(sortInfo, [...documentHeaders]);
        setSortedDocumentHeaders(sortableDocumentHeaders);
    }, [documentHeaders, sortInfo]);

    const sortByProp = (propName: PVTableSortField) => {
        const newSortInfo = getPVSortInfoForProp(sortInfo.direction, sortInfo.propName, propName);

        setSortInfo(newSortInfo);

        if (newSortInfo.direction === SortDirection.UNSORTED) {
            setSortedDocumentHeaders([...documentHeaders]);
        }
    };

    const sortableTableHeader = (prop: PVTableSortField, translationKey: string, qaId: string) => (
        <Th
            onClick={() => sortByProp(prop)}
            qa-id={qaId}
            icon={
                sortInfo?.propName === prop ? getSortDirectionIcon(sortInfo.direction) : undefined
            }>
            {t(translationKey)}
        </Th>
    );

    return (
        <div>
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
                        <WriteAccessRequired>
                            <Th></Th>
                        </WriteAccessRequired>
                    </tr>
                </thead>
                <tbody>
                    {sortedDocumentHeaders.map((item) => (
                        <PVFileListRow
                            listMode={listMode}
                            key={item.document.id}
                            item={item}
                            onReject={() => onReject([item.document.id])}
                            onImport={() => onImport(item.document.id)}
                            onRestore={() => onRestore(item.document.id)}
                            onRejectByProject={(oid: Oid) => rejectByProject(oid)}
                            onRejectByProjectGroup={(oid: Oid) => rejectByProjectGroup(oid)}
                            onRejectByAssignment={(oid: Oid) => rejectByAssignment(oid)}
                            changeTime={changeTime}
                            itemCounts={{
                                assignment: documentHeaders.filter(
                                    (document) =>
                                        document.assignment &&
                                        isAssignment(document, item.assignment?.oid),
                                ).length,
                                project: documentHeaders.filter(
                                    (document) =>
                                        document.project && isProject(document, item.project?.oid),
                                ).length,
                                projectGroup: documentHeaders.filter(
                                    (document) =>
                                        document.projectGroup &&
                                        isProjectGroup(document, item.projectGroup?.oid),
                                ).length,
                            }}
                        />
                    ))}
                </tbody>
            </Table>
        </div>
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
    itemCounts,
}: PVFileListRowProps) => {
    const { t } = useTranslation();

    const [isOpen, setIsOpen] = React.useState(false);
    const [fileActionMenuVisible, setFileActionMenuVisible] = React.useState(false);
    const [showConfirmAssignmentReject, setShowConfirmAssignmentReject] = useState(false);
    const [showConfirmProjectReject, setShowConfirmProjectReject] = useState(false);
    const [showConfirmProjectGroupReject, setShowConfirmProjectGroupReject] = useState(false);

    const actionMenuRef = React.useRef(null);

    const fileActions = [
        {
            disabled: !item.assignment?.oid,
            onSelect: () => {
                setShowConfirmAssignmentReject(true);
                setFileActionMenuVisible(false);
            },
            name: t('projektivelho.file-list.reject-by-assignment', {
                assignmentCount: itemCounts.assignment ? itemCounts.assignment : '-',
            }),
        },
        {
            disabled: !item.project?.oid,
            onSelect: () => {
                setShowConfirmProjectReject(true);
                setFileActionMenuVisible(false);
            },
            name: t('projektivelho.file-list.reject-by-project', {
                projectCount: itemCounts.project ? itemCounts.project : '-',
            }),
        },
        {
            disabled: !item.projectGroup?.oid,
            onSelect: () => {
                setShowConfirmProjectGroupReject(true);
                setFileActionMenuVisible(false);
            },
            name: t('projektivelho.file-list.reject-by-project-group', {
                groupCount: itemCounts.projectGroup ? itemCounts.projectGroup : '-',
            }),
        },
    ];

    const confirmAssignmentRejectDialog = () => (
        <Dialog
            className={'dialog--wide'}
            variant={DialogVariant.LIGHT}
            title={t('projektivelho.file-list.reject-by-assignment-title')}
            onClose={() => setShowConfirmAssignmentReject(false)}
            footerContent={
                <React.Fragment>
                    <Button
                        variant={ButtonVariant.SECONDARY}
                        onClick={() => setShowConfirmAssignmentReject(false)}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        onClick={() => {
                            item.assignment && onRejectByAssignment(item.assignment?.oid);
                            setShowConfirmAssignmentReject(false);
                        }}>
                        {t('projektivelho.file-list.reject-confirm', {
                            count: itemCounts.assignment,
                        })}
                    </Button>
                </React.Fragment>
            }>
            <Trans
                i18nKey="projektivelho.file-list.reject-by-assignment-message"
                assignment={item.assignment?.name}
                assignmentCount={itemCounts.assignment}></Trans>
        </Dialog>
    );
    const confirmProjectRejectDialog = () => (
        <Dialog
            className={'dialog--wide'}
            variant={DialogVariant.LIGHT}
            title={t('projektivelho.file-list.reject-by-project-title')}
            onClose={() => setShowConfirmProjectReject(false)}
            footerContent={
                <React.Fragment>
                    <Button
                        variant={ButtonVariant.SECONDARY}
                        onClick={() => setShowConfirmProjectReject(false)}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        onClick={() => {
                            item.project && onRejectByProject(item.project?.oid);
                            setShowConfirmProjectReject(false);
                        }}>
                        {t('projektivelho.file-list.reject-confirm', { count: itemCounts.project })}
                    </Button>
                </React.Fragment>
            }>
            <Trans
                i18nKey="projektivelho.file-list.reject-by-project-message"
                projectName={item.project?.name}
                projectCount={itemCounts.project}
            />
        </Dialog>
    );
    const confirmProjectGroupRejectDialog = () => (
        <Dialog
            className={'dialog--wide'}
            variant={DialogVariant.LIGHT}
            title={t('projektivelho.file-list.reject-by-project-group-title')}
            onClose={() => setShowConfirmProjectGroupReject(false)}
            footerContent={
                <React.Fragment>
                    <Button
                        variant={ButtonVariant.SECONDARY}
                        onClick={() => setShowConfirmProjectGroupReject(false)}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        onClick={() => {
                            item.projectGroup && onRejectByProjectGroup(item.projectGroup?.oid);
                            setShowConfirmProjectGroupReject(false);
                        }}>
                        {t('projektivelho.file-list.reject-confirm', {
                            count: itemCounts.projectGroup,
                        })}
                    </Button>
                </React.Fragment>
            }>
            <Trans
                i18nKey="projektivelho.file-list.reject-by-project-group-message"
                projectGroup={item.projectGroup?.name}
                groupCount={itemCounts.projectGroup}
            />
        </Dialog>
    );

    return (
        <>
            <tr key={`${item.document.id}`}>
                <td>
                    <AccordionToggle open={isOpen} onToggle={() => setIsOpen(!isOpen)} />
                </td>
                <td>{item.project && item.project.name}</td>
                <td>
                    <Link
                        className={styles['projektivelho-file-list__link']}
                        href={projektivelhoDocumentDownloadUri(item.document.id)}>
                        {item.document.name}
                    </Link>
                </td>
                <td>{item.document.description}</td>
                <td>{formatDateFull(item.document.modified)}</td>
                <WriteAccessRequired>
                    <td>
                        <div
                            className={styles['projektivelho-file-list__buttons']}
                            ref={actionMenuRef}>
                            {listMode === 'SUGGESTED' && (
                                <Button
                                    title={t('projektivelho.file-list.reject-tooltip')}
                                    variant={ButtonVariant.SECONDARY}
                                    onClick={onReject}
                                    qa-id="pv-reject-button">
                                    {t('projektivelho.file-list.reject')}
                                </Button>
                            )}
                            {listMode === 'REJECTED' && (
                                <Button
                                    title={t('projektivelho.file-list.restore-tooltip')}
                                    variant={ButtonVariant.SECONDARY}
                                    onClick={onRestore}
                                    qa-id="pv-restore-button">
                                    {t('projektivelho.file-list.restore')}
                                </Button>
                            )}
                            <Button
                                title={t('projektivelho.file-list.upload-tooltip')}
                                variant={ButtonVariant.SECONDARY}
                                onClick={onImport}
                                qa-id="pv-import-button">
                                {t('projektivelho.file-list.upload')}
                            </Button>
                            {listMode === 'SUGGESTED' && (
                                <Button
                                    title={t('projektivelho.file-list.more')}
                                    variant={ButtonVariant.SECONDARY}
                                    onClick={() => {
                                        setFileActionMenuVisible(!fileActionMenuVisible);
                                    }}
                                    qa-id="pv-menu-button">
                                    {'...'}
                                </Button>
                            )}
                        </div>
                        {fileActionMenuVisible && (
                            <Menu
                                positionRef={actionMenuRef}
                                items={fileActions}
                                onClickOutside={() => setFileActionMenuVisible(false)}
                            />
                        )}
                        {showConfirmAssignmentReject && confirmAssignmentRejectDialog()}
                        {showConfirmProjectReject && confirmProjectRejectDialog()}
                        {showConfirmProjectGroupReject && confirmProjectGroupRejectDialog()}
                    </td>
                </WriteAccessRequired>
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

const PVFileListExpandedItem = ({ item, changeTime: _changeTime }: PVFileListExpandedItemProps) => {
    const { t } = useTranslation();
    return (
        <div className={styles['projektivelho-file-list__expanded']}>
            <InfoboxContent>
                {item.projectGroup && (
                    <InfoboxField
                        label={t('projektivelho.file-list.field.project-group')}
                        value={
                            item.projectGroup.name
                            // TODO Re-enable redirect links when they work
                            /*<PVRedirectLink changeTime={changeTime} oid={item.projectGroup.oid}>
                                {`${item.projectGroup.name} (${item.projectGroup.state})`}
                            </PVRedirectLink>*/
                        }
                    />
                )}
                {item.project && (
                    <InfoboxField
                        label={t('projektivelho.file-list.field.project-name')}
                        value={
                            item.project.name
                            // TODO Re-enable redirect links when they work
                            /*<PVRedirectLink changeTime={changeTime} oid={item.project.oid}>
                                {`${item.project.name} (${item.project.state})`}
                            </PVRedirectLink>*/
                        }
                    />
                )}
                {item.assignment && (
                    <InfoboxField
                        label={t('projektivelho.file-list.field.assignment')}
                        value={
                            item.assignment.name
                            // TODO Re-enable redirect links when they work
                            /*<PVRedirectLink changeTime={changeTime} oid={item.assignment.oid}>
                                {`${item.assignment.name} (${item.assignment.state})`}
                            </PVRedirectLink>*/
                        }
                    />
                )}
            </InfoboxContent>
            <InfoboxContent>
                <InfoboxField
                    label={t('projektivelho.file-list.field.material-group')}
                    value={`${item.document.group} / ${item.document.category}`}
                />
                <InfoboxField
                    label={t('projektivelho.file-list.field.document-type')}
                    value={item.document.type}
                />
                <InfoboxField
                    label={t('projektivelho.file-list.field.document-state')}
                    value={item.document.state}
                />
            </InfoboxContent>
        </div>
    );
};
