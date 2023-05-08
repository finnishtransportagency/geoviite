import * as React from 'react';
import { Table, Th } from 'vayla-design-lib/table/table';
import { useTranslation } from 'react-i18next';
import styles from './velho-file-list.scss';
import { AccordionToggle } from 'vayla-design-lib/accordion-toggle/accordion-toggle';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { formatDateFull } from 'utils/date-utils';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { VelhoDocumentHeader, VelhoDocumentId } from './velho-model';
import { useCommonDataAppSelector } from 'store/hooks';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { updateVelhoDocumentsChangeTime } from 'common/change-time-api';
import { useAppNavigate } from 'common/navigate';
import { getVelhoDocuments, rejectVelhoDocument } from 'infra-model/infra-model-api';

type VelhoFileListProps = {
    documentHeaders: VelhoDocumentHeader[];
    isLoading: boolean;
    onReject: (id: VelhoDocumentId) => void;
    onImport: (id: VelhoDocumentId) => void;
};

export const VelhoFileListContainer: React.FC = () => {
    const navigate = useAppNavigate();
    const changeTime = useCommonDataAppSelector((state) => state.changeTimes.velhoDocument);
    const [documentHeaders, loadStatus] =
        useLoaderWithStatus(() => {
            return getVelhoDocuments(changeTime, 'PENDING');
        }, [changeTime]) || [];
    return (
        <VelhoFileList
            documentHeaders={documentHeaders || []}
            isLoading={loadStatus != LoaderStatus.Ready}
            onReject={(id) => rejectVelhoDocument(id).then(() => updateVelhoDocumentsChangeTime())}
            onImport={(id) => navigate('inframodel-import', id)}
        />
    );
};

export const VelhoFileList = ({
    documentHeaders,
    isLoading,
    onReject,
    onImport,
}: VelhoFileListProps) => {
    const { t } = useTranslation();

    const [openItemId, setOpenItemId] = React.useState<string | null>(null);

    return (
        <div>
            <p>{isLoading ? 'LOADING' : 'DONE'}</p>
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
                            key={item.id}
                            item={item}
                            isOpen={item.id === openItemId}
                            onToggleOpen={() =>
                                item.id === openItemId
                                    ? setOpenItemId(null)
                                    : setOpenItemId(item.id)
                            }
                            onReject={() => onReject(item.id)}
                            onImport={() => onImport(item.id)}
                        />
                    ))}
                </tbody>
            </Table>
        </div>
    );
};

type VelhoFileListRowProps = {
    item: VelhoDocumentHeader;
    isOpen: boolean;
    onToggleOpen: () => void;
    onReject: () => void;
    onImport: () => void;
};

const VelhoFileListRow = ({
    item,
    isOpen,
    onToggleOpen,
    onReject,
    onImport,
}: VelhoFileListRowProps) => {
    const { t } = useTranslation();
    return (
        <>
            <tr key={`${item.id}`}>
                <td>
                    <AccordionToggle open={isOpen} onToggle={() => onToggleOpen()} />
                </td>
                <td>{item.project.name}</td>
                <td>{item.document.name}</td>
                <td>{item.document.description}</td>
                <td>{formatDateFull(item.document.modified)}</td>
                <td>
                    <div className={styles['velho-file-list__buttons']}>
                        <Button variant={ButtonVariant.SECONDARY} onClick={onReject}>
                            {t('velho.file-list.reject')}
                        </Button>
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
    item: VelhoDocumentHeader;
};

const VelhoFileListExpandedItem = ({ item }: VelhoFileListExpandedItemProps) => {
    const { t } = useTranslation();
    return (
        <div className={styles['velho-file-list__expanded']}>
            <InfoboxContent>
                <InfoboxField
                    label={t('velho.file-list.field.project-group')}
                    value={item.project.group}
                />
                <InfoboxField
                    label={t('velho.file-list.field.project-name')}
                    value={item.project.name}
                />
                <InfoboxField
                    label={t('velho.file-list.field.assignment')}
                    value={item.assignment}
                />
            </InfoboxContent>
            <InfoboxContent>
                <InfoboxField
                    label={t('velho.file-list.field.material-group')}
                    value={item.materialGroup}
                />
                <InfoboxField
                    label={t('velho.file-list.field.document-type')}
                    value={item.document.type.name}
                />
            </InfoboxContent>
        </div>
    );
};
