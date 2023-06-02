import * as React from 'react';
import { Table, Th } from 'vayla-design-lib/table/table';
import { useTranslation } from 'react-i18next';
import styles from './velho-file-list.scss';
import { AccordionToggle } from 'vayla-design-lib/accordion-toggle/accordion-toggle';
import { formatDateFull } from 'utils/date-utils';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { PVDocumentHeader, PVDocumentId } from './velho-model';
import { useAppNavigate } from 'common/navigate';
import {
    getVelhoDocuments,
    projektivelhoDocumentDownloadUri,
    rejectVelhoDocument,
    restoreVelhoDocument,
} from 'infra-model/infra-model-api';
import { updateVelhoDocumentsChangeTime } from 'common/change-time-api';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { TimeStamp } from 'common/common-model';
import { Link } from 'vayla-design-lib/link/link';
import { VelhoRedirectLink } from 'infra-model/velho/velho-redirect-link';

type ListMode = 'SUGGESTED' | 'REJECTED';

type VelhoFileListContainerProps = {
    changeTime: TimeStamp;
    listMode: ListMode;
};

type VelhoFileListProps = {
    documentHeaders: PVDocumentHeader[];
    isLoading: boolean;
    listMode: ListMode;
    onReject: (id: PVDocumentId) => void;
    onImport: (id: PVDocumentId) => void;
    onRestore: (id: PVDocumentId) => void;
    changeTime: TimeStamp;
};

export const VelhoFileListContainer: React.FC<VelhoFileListContainerProps> = ({
    changeTime,
    listMode,
}: VelhoFileListContainerProps) => {
    const navigate = useAppNavigate();
    const [documentHeaders, isLoading] =
        useLoaderWithStatus(() => {
            return getVelhoDocuments(changeTime, listMode);
        }, [changeTime]) || [];

    return (
        <div className="velho-file-list">
            <VelhoFileList
                documentHeaders={documentHeaders || []}
                isLoading={isLoading !== LoaderStatus.Ready}
                onReject={(id) =>
                    rejectVelhoDocument(id).then(() => updateVelhoDocumentsChangeTime())
                }
                onImport={(id) => navigate('inframodel-import', id)}
                onRestore={(id) =>
                    restoreVelhoDocument(id).then(() => updateVelhoDocumentsChangeTime())
                }
                listMode={listMode}
                changeTime={changeTime}
            />
        </div>
    );
};

export const VelhoFileList = ({
    documentHeaders,
    isLoading,
    onReject,
    onImport,
    onRestore,
    listMode,
    changeTime,
}: VelhoFileListProps) => {
    const { t } = useTranslation();
    const [openItemId, setOpenItemId] = React.useState<string | null>(null);

    return (
        <div>
            <Table className={styles['velho-file-list__table']} wide isLoading={isLoading}>
                <thead>
                    <tr>
                        <Th></Th>
                        <Th>{t('velho.file-list.header.project-name')}</Th>
                        <Th>{t('velho.file-list.header.document-name')}</Th>
                        <Th>{t('velho.file-list.header.document-description')}</Th>
                        <Th>{t('velho.file-list.header.document-modified')}</Th>
                        <Th></Th>
                    </tr>
                </thead>
                <tbody>
                    {documentHeaders.map((item) => (
                        <VelhoFileListRow
                            listMode={listMode}
                            key={item.document.id}
                            item={item}
                            isOpen={item.document.id === openItemId}
                            onToggleOpen={() =>
                                item.document.id === openItemId
                                    ? setOpenItemId(null)
                                    : setOpenItemId(item.document.id)
                            }
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

type VelhoFileListRowProps = {
    item: PVDocumentHeader;
    listMode: ListMode;
    isOpen: boolean;
    onToggleOpen: () => void;
    onReject: () => void;
    onImport: () => void;
    onRestore: () => void;
    changeTime: TimeStamp;
};

const VelhoFileListRow = ({
    item,
    listMode,
    isOpen,
    onToggleOpen,
    onReject,
    onImport,
    onRestore,
    changeTime,
}: VelhoFileListRowProps) => {
    const { t } = useTranslation();
    return (
        <>
            <tr key={`${item.document.id}`}>
                <td>
                    <AccordionToggle open={isOpen} onToggle={() => onToggleOpen()} />
                </td>
                <td>{item.project && item.project.name}</td>
                <td>
                    <Link
                        className={styles['velho-file-list__link']}
                        href={projektivelhoDocumentDownloadUri(item.document.id)}>
                        {item.document.name}
                    </Link>
                </td>
                <td>{item.document.description}</td>
                <td>{formatDateFull(item.document.modified)}</td>
                <td>
                    <div className={styles['velho-file-list__buttons']}>
                        {listMode === 'SUGGESTED' && (
                            <Button
                                title={t('velho.file-list.reject-tooltip')}
                                variant={ButtonVariant.SECONDARY}
                                onClick={onReject}>
                                {t('velho.file-list.reject')}
                            </Button>
                        )}
                        {listMode === 'REJECTED' && (
                            <Button
                                title={t('velho.file-list.restore-tooltip')}
                                variant={ButtonVariant.SECONDARY}
                                onClick={onRestore}>
                                {t('velho.file-list.restore')}
                            </Button>
                        )}
                        <Button
                            title={t('velho.file-list.upload-tooltip')}
                            variant={ButtonVariant.SECONDARY}
                            onClick={onImport}>
                            {t('velho.file-list.upload')}
                        </Button>
                    </div>
                </td>
            </tr>
            {isOpen ? (
                <tr>
                    <td></td>
                    <td colSpan={5}>
                        <VelhoFileListExpandedItem item={item} changeTime={changeTime} />
                    </td>
                </tr>
            ) : (
                <></>
            )}
        </>
    );
};

type VelhoFileListExpandedItemProps = {
    item: PVDocumentHeader;
    changeTime: TimeStamp;
};

const VelhoFileListExpandedItem = ({ item, changeTime }: VelhoFileListExpandedItemProps) => {
    const { t } = useTranslation();
    return (
        <div className={styles['velho-file-list__expanded']}>
            <InfoboxContent>
                {item.projectGroup && (
                    <InfoboxField
                        label={t('velho.file-list.field.project-group')}
                        value={
                            <VelhoRedirectLink changeTime={changeTime} oid={item.projectGroup.oid}>
                                {`${item.projectGroup.name} (${item.projectGroup.state})`}
                            </VelhoRedirectLink>
                        }
                    />
                )}
                {item.project && (
                    <InfoboxField
                        label={t('velho.file-list.field.project-name')}
                        value={
                            <VelhoRedirectLink changeTime={changeTime} oid={item.project.oid}>
                                {`${item.project.name} (${item.project.state})`}
                            </VelhoRedirectLink>
                        }
                    />
                )}
                {item.assignment && (
                    <InfoboxField
                        label={t('velho.file-list.field.assignment')}
                        value={
                            <VelhoRedirectLink changeTime={changeTime} oid={item.assignment.oid}>
                                {`${item.assignment.name} (${item.assignment.state})`}
                            </VelhoRedirectLink>
                        }
                    />
                )}
            </InfoboxContent>
            <InfoboxContent>
                <InfoboxField
                    label={t('velho.file-list.field.material-group')}
                    value={`${item.document.group} / ${item.document.category}`}
                />
                <InfoboxField
                    label={t('velho.file-list.field.document-type')}
                    value={item.document.type}
                />
                <InfoboxField
                    label={t('velho.file-list.field.document-state')}
                    value={item.document.state}
                />
            </InfoboxContent>
        </div>
    );
};
