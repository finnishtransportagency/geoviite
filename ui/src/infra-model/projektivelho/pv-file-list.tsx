import * as React from 'react';
import { Table, Th } from 'vayla-design-lib/table/table';
import { useTranslation } from 'react-i18next';
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
import { PVRedirectLink } from 'infra-model/projektivelho/pv-redirect-link';
import { useState } from 'react';
import { WriteAccessRequired } from 'user/write-access-required';
import { useContextMenu, Menu, Item } from 'react-contexify';
import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';

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

    const rejectByAssignment = (assignmentOid: string) =>
        rejectByFilter((item: PVDocumentHeader) => item.assignment?.oid === assignmentOid);
    const rejectByProject = (projectOid: string) =>
        rejectByFilter((item: PVDocumentHeader) => item.project?.oid === projectOid);
    const rejectByProjectGroup = (projectGroupOid: string) =>
        rejectByFilter((item: PVDocumentHeader) => item.projectGroup?.oid === projectGroupOid);

    return (
        <div>
            <Table className={styles['projektivelho-file-list__table']} wide isLoading={isLoading}>
                <thead>
                    <tr>
                        <Th></Th>
                        <Th qa-id="projektivelho.project-name">
                            {t('projektivelho.file-list.header.project-name')}
                        </Th>
                        <Th qa-id="projektivelho.document-name">
                            {t('projektivelho.file-list.header.document-name')}
                        </Th>
                        <Th qa-id="projektivelho.document-description">
                            {t('projektivelho.file-list.header.document-description')}
                        </Th>
                        <Th qa-id="projektivelho.document-modified">
                            {t('projektivelho.file-list.header.document-modified')}
                        </Th>
                        <WriteAccessRequired>
                            <Th></Th>
                        </WriteAccessRequired>
                    </tr>
                </thead>
                <tbody>
                    {documentHeaders.map((item) => (
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
                                    (i) =>
                                        i.assignment !== null &&
                                        i.assignment?.oid === item.assignment?.oid,
                                ).length,
                                project: documentHeaders.filter(
                                    (i) =>
                                        i.project !== null && i.project?.oid === item.project?.oid,
                                ).length,
                                projectGroup: documentHeaders.filter(
                                    (i) =>
                                        i.projectGroup !== null &&
                                        i.projectGroup?.oid === item.projectGroup?.oid,
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

    const menuId = () => `contextmenu_${item.document.id}}`;
    const { show: showContextMenu, hideAll: hideContextMenu } = useContextMenu({
        id: menuId(),
    });
    const [showConfirmAssignmentReject, setShowConfirmAssignmentReject] = useState(false);
    const [showConfirmProjectReject, setShowConfirmProjectReject] = useState(false);
    const [showConfirmProjectGroupReject, setShowConfirmProjectGroupReject] = useState(false);

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
                        <div className={styles['projektivelho-file-list__buttons']}>
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
                                    onClick={(event: React.MouseEvent) => {
                                        showContextMenu({ event });
                                    }}
                                    qa-id="pv-menu-button">
                                    {'...'}
                                </Button>
                            )}
                        </div>
                        <div>
                            <Menu animation={false} id={menuId()}>
                                <Item
                                    id="1"
                                    disabled={!item.assignment?.oid}
                                    onClick={() => {
                                        setShowConfirmAssignmentReject(true);
                                        hideContextMenu();
                                    }}>
                                    {t('projektivelho.file-list.reject-by-assignment', [
                                        itemCounts.assignment ? itemCounts.assignment : '-',
                                    ])}
                                </Item>
                                <Item
                                    id="2"
                                    disabled={!item.project?.oid}
                                    onClick={() => {
                                        setShowConfirmProjectReject(true);
                                        hideContextMenu();
                                    }}>
                                    {t('projektivelho.file-list.reject-by-project', [
                                        itemCounts.project ? itemCounts.project : '-',
                                    ])}
                                </Item>
                                <Item
                                    id="3"
                                    disabled={!item.projectGroup?.oid}
                                    onClick={() => {
                                        setShowConfirmProjectGroupReject(true);
                                        hideContextMenu();
                                    }}>
                                    {t('projektivelho.file-list.reject-by-project-group', [
                                        itemCounts.projectGroup ? itemCounts.projectGroup : '-',
                                    ])}
                                </Item>
                            </Menu>
                        </div>
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
            {showConfirmAssignmentReject && (
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
                                {t('projektivelho.file-list.reject-confirm', [
                                    itemCounts.assignment,
                                ])}
                            </Button>
                        </React.Fragment>
                    }>
                    <span
                        dangerouslySetInnerHTML={{
                            __html: t('projektivelho.file-list.reject-by-assignment-message', [
                                item.assignment?.name,
                                itemCounts.assignment,
                            ]),
                        }}
                    />
                </Dialog>
            )}
            {showConfirmProjectReject && (
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
                                {t('projektivelho.file-list.reject-confirm', [itemCounts.project])}
                            </Button>
                        </React.Fragment>
                    }>
                    <span
                        dangerouslySetInnerHTML={{
                            __html: t('projektivelho.file-list.reject-by-project-message', [
                                item.project?.name,
                                itemCounts.project,
                            ]),
                        }}
                    />
                </Dialog>
            )}
            {showConfirmProjectGroupReject && (
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
                                    item.projectGroup &&
                                        onRejectByProjectGroup(item.projectGroup?.oid);
                                    setShowConfirmProjectGroupReject(false);
                                }}>
                                {t('projektivelho.file-list.reject-confirm', [
                                    itemCounts.projectGroup,
                                ])}
                            </Button>
                        </React.Fragment>
                    }>
                    <span
                        dangerouslySetInnerHTML={{
                            __html: t('projektivelho.file-list.reject-by-project-group-message', [
                                item.projectGroup?.name,
                                itemCounts.projectGroup,
                            ]),
                        }}
                    />
                </Dialog>
            )}
        </>
    );
};

type PVFileListExpandedItemProps = {
    item: PVDocumentHeader;
    changeTime: TimeStamp;
};

const PVFileListExpandedItem = ({ item, changeTime }: PVFileListExpandedItemProps) => {
    const { t } = useTranslation();
    return (
        <div className={styles['projektivelho-file-list__expanded']}>
            <InfoboxContent>
                {item.projectGroup && (
                    <InfoboxField
                        label={t('projektivelho.file-list.field.project-group')}
                        value={
                            <PVRedirectLink changeTime={changeTime} oid={item.projectGroup.oid}>
                                {`${item.projectGroup.name} (${item.projectGroup.state})`}
                            </PVRedirectLink>
                        }
                    />
                )}
                {item.project && (
                    <InfoboxField
                        label={t('projektivelho.file-list.field.project-name')}
                        value={
                            <PVRedirectLink changeTime={changeTime} oid={item.project.oid}>
                                {`${item.project.name} (${item.project.state})`}
                            </PVRedirectLink>
                        }
                    />
                )}
                {item.assignment && (
                    <InfoboxField
                        label={t('projektivelho.file-list.field.assignment')}
                        value={
                            <PVRedirectLink changeTime={changeTime} oid={item.assignment.oid}>
                                {`${item.assignment.name} (${item.assignment.state})`}
                            </PVRedirectLink>
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
