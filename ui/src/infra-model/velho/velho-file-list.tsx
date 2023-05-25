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
    rejectVelhoDocument,
    restoreVelhoDocument,
} from 'infra-model/infra-model-api';
import { updateVelhoDocumentsChangeTime } from 'common/change-time-api';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { TimeStamp } from 'common/common-model';

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
};

const VelhoFileListRow = ({
    item,
    listMode,
    isOpen,
    onToggleOpen,
    onReject,
    onImport,
    onRestore,
}: VelhoFileListRowProps) => {
    const { t } = useTranslation();
    return (
        <>
            <tr key={`${item.document.id}`}>
                <td>
                    <AccordionToggle open={isOpen} onToggle={() => onToggleOpen()} />
                </td>
                {item.project && <td>{item.project.name}</td>}
                <td>{item.document.name}</td>
                <td>{item.document.description}</td>
                <td>{formatDateFull(item.document.modified)}</td>
                <td>
                    <div className={styles['velho-file-list__buttons']}>
                        {listMode === 'SUGGESTED' && (
                            <Button variant={ButtonVariant.SECONDARY} onClick={onReject}>
                                {t('velho.file-list.reject')}
                            </Button>
                        )}
                        {listMode === 'REJECTED' && (
                            <Button variant={ButtonVariant.SECONDARY} onClick={onRestore}>
                                {t('velho.file-list.restore')}
                            </Button>
                        )}
                        <Button variant={ButtonVariant.SECONDARY} onClick={onImport}>
                            {t('velho.file-list.upload')}
                        </Button>
                    </div>
                </td>
            </tr>
            {isOpen ? (
                <tr>
                    <td></td>
                    <td colSpan={5}>
                        <VelhoFileListExpandedItem item={item} />
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
};

const VelhoFileListExpandedItem = ({ item }: VelhoFileListExpandedItemProps) => {
    const { t } = useTranslation();
    return (
        <div className={styles['velho-file-list__expanded']}>
            <InfoboxContent>
                {item.projectGroup && (
                    <InfoboxField
                        label={t('velho.file-list.field.project-group')}
                        value={item.projectGroup.name}
                    />
                )}
                {item.project && (
                    <InfoboxField
                        label={t('velho.file-list.field.project-name')}
                        value={item.project.name}
                    />
                )}
                {item.assignment && (
                    <InfoboxField
                        label={t('velho.file-list.field.assignment')}
                        value={item.assignment.name}
                    />
                )}
            </InfoboxContent>
            <InfoboxContent>
                <InfoboxField
                    label={t('velho.file-list.field.material-group')}
                    value={item.document.group}
                />
                <InfoboxField
                    label={t('velho.file-list.field.document-type')}
                    value={item.document.type}
                />
            </InfoboxContent>
        </div>
    );
};
