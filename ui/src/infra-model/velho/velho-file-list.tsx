import * as React from 'react';
import { Table, Th } from 'vayla-design-lib/table/table';
import { useTranslation } from 'react-i18next';
import styles from './velho-file-list.scss';
import { useEffect } from 'react';
import { TimeStamp } from 'common/common-model';
import { AccordionToggle } from 'vayla-design-lib/accordion-toggle/accordion-toggle';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { formatDateFull } from 'utils/date-utils';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';

const dummyData: VelhoFileHeader[] = [
    {
        id: '1',
        project: {
            name: 'test project',
            group: 'test group',
        },
        assignment: 'Rakentamissuunnittelu',
        materialGroup: 'Suunnitelma / suunnitelmakokonaisuus',
        document: {
            name: 'test1',
            description: 'asdf/zxcv/test1',
            type: 'Suunnitelmamalli',
            modified: '2023-03-05T10:53:00.000Z',
            status: 'PENDING',
        },
    },
    {
        id: '2',
        project: {
            name: 'test project',
            group: 'test group',
        },
        assignment: 'Rakentamissuunnittelu',
        materialGroup: 'Suunnitelma / suunnitelmakokonaisuus',
        document: {
            name: 'test2',
            description: 'asdf/zxcv/test2',
            type: 'Suunnitelmamalli',
            modified: '2023-03-05T11:53:00.000Z',
            status: 'PENDING',
        },
    },
    {
        id: '3',
        project: {
            name: 'test project',
            group: 'test group',
        },
        assignment: 'Rakentamissuunnittelu',
        materialGroup: 'Suunnitelma / suunnitelmakokonaisuus',
        document: {
            name: 'test3',
            description: 'asdf/zxcv/test3',
            type: 'Suunnitelmamalli',
            modified: '2023-03-05T12:53:00.000Z',
            status: 'PENDING',
        },
    },
];

export type VelhoDocumentType = 'Suunnitelmamalli';
export type VelhoFileStatus = 'REJECTED' | 'ACCEPTED' | 'PENDING';
export type VelhoProject = {
    group: string;
    name: string;
};
export type VelhoDocument = {
    name: string;
    description: string;
    type: VelhoDocumentType;
    modified: TimeStamp;
    status: VelhoFileStatus;
};
export type VelhoFileHeader = {
    id: string;
    project: VelhoProject;
    assignment: string;
    materialGroup: string;
    document: VelhoDocument;
};

type VelhoFileListProps = {
    _asdf: string;
};

export const VelhoFileList = ({ _asdf }: VelhoFileListProps) => {
    const { t } = useTranslation();

    const [isLoading, setIsLoading] = React.useState(false);
    const [fileHeaders, _setFileHeaders] = React.useState<VelhoFileHeader[]>(dummyData);
    const [openItemId, setOpenItemId] = React.useState<string | null>(null);

    useEffect(() => {
        const fakeLoad = setTimeout(
            () => {
                console.log('Toggle loading');
                setIsLoading(!isLoading);
            },
            isLoading ? 1000 : 10000,
        );
        return () => clearTimeout(fakeLoad);
    }, [isLoading]);

    return (
        <>
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
                        {fileHeaders.map((item) => {
                            return (
                                <VelhoFileListRow
                                    key={item.id}
                                    item={item}
                                    isOpen={item.id === openItemId}
                                    onToggleOpen={() =>
                                        item.id === openItemId
                                            ? setOpenItemId(null)
                                            : setOpenItemId(item.id)
                                    }
                                />
                            );
                        })}
                    </tbody>
                </Table>
            </div>
        </>
    );
};

type VelhoFileListRowProps = {
    item: VelhoFileHeader;
    isOpen: boolean;
    onToggleOpen: () => void;
};

const VelhoFileListRow = ({ item, isOpen, onToggleOpen }: VelhoFileListRowProps) => {
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
                    <Button variant={ButtonVariant.SECONDARY}>{t('velho.file-list.reject')}</Button>
                    <Button variant={ButtonVariant.SECONDARY}>{t('velho.file-list.upload')}</Button>
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
    item: VelhoFileHeader;
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
                    value={item.document.type}
                />
            </InfoboxContent>
        </div>
    );
};
