import { Operation } from 'publication/publication-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

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
    a: { operation: Operation },
    b: { operation: Operation },
) => publicationOperationSortPriority(b.operation) - publicationOperationSortPriority(a.operation);
