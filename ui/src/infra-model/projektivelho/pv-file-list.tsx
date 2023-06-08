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
    rejectPVDocument,
    restorePVDocument,
} from 'infra-model/infra-model-api';
import { updatePVDocumentsChangeTime } from 'common/change-time-api';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { TimeStamp } from 'common/common-model';
import { Link } from 'vayla-design-lib/link/link';
import { PVRedirectLink } from 'infra-model/projektivelho/pv-redirect-link';
import { useState } from 'react';
import { WriteAccessRequired } from 'user/write-access-required';

type ListMode = 'SUGGESTED' | 'REJECTED';

type PVFileListContainerProps = {
    changeTime: TimeStamp;
    listMode: ListMode;
};

type PVFileListProps = {
    documentHeaders: PVDocumentHeader[];
    isLoading: boolean;
    listMode: ListMode;
    onReject: (id: PVDocumentId) => void;
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
                onReject={(id) =>
                    rejectPVDocument(id).then(() => {
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

    return (
        <div>
            <Table className={styles['projektivelho-file-list__table']} wide isLoading={isLoading}>
                <thead>
                    <tr>
                        <Th></Th>
                        <Th>{t('projektivelho.file-list.header.project-name')}</Th>
                        <Th>{t('projektivelho.file-list.header.document-name')}</Th>
                        <Th>{t('projektivelho.file-list.header.document-description')}</Th>
                        <Th>{t('projektivelho.file-list.header.document-modified')}</Th>
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
                            onReject={() => onReject(item.document.id)}
                            onImport={() => onImport(item.document.id)}
                            onRestore={() => onRestore(item.document.id)}
                            changeTime={changeTime}
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
};

const PVFileListRow = ({
    item,
    listMode,
    onReject,
    onImport,
    onRestore,
    changeTime,
}: PVFileListRowProps) => {
    const { t } = useTranslation();
    const [isOpen, setIsOpen] = React.useState(false);
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
                                    onClick={onReject}>
                                    {t('projektivelho.file-list.reject')}
                                </Button>
                            )}
                            {listMode === 'REJECTED' && (
                                <Button
                                    title={t('projektivelho.file-list.restore-tooltip')}
                                    variant={ButtonVariant.SECONDARY}
                                    onClick={onRestore}>
                                    {t('projektivelho.file-list.restore')}
                                </Button>
                            )}
                            <Button
                                title={t('projektivelho.file-list.upload-tooltip')}
                                variant={ButtonVariant.SECONDARY}
                                onClick={onImport}>
                                {t('projektivelho.file-list.upload')}
                            </Button>
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
