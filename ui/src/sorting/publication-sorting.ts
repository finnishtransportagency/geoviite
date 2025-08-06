import { Operation, PublicationTableItem } from 'publication/publication-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { ChangeTableEntry } from 'preview/change-table-entry-mapping';

export const publicationOperationSortPriority = (operation: Operation | undefined) => {
    switch (operation) {
        case 'CREATE':
            return 5;
        case 'MODIFY':
            return 4;
        case 'DELETE':
            return 3;
        case 'RESTORE':
            return 2;
        case 'CALCULATED':
            return 1;
        case undefined:
            return 0;

        default:
            return exhaustiveMatchingGuard(operation);
    }
};

export const publicationOperationCompare = (
    a: Pick<ChangeTableEntry, 'operation'> | Pick<PublicationTableItem, 'operation'>,
    b: Pick<ChangeTableEntry, 'operation'> | Pick<PublicationTableItem, 'operation'>,
) => publicationOperationSortPriority(b.operation) - publicationOperationSortPriority(a.operation);
