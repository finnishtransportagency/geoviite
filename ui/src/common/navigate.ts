import { GeometryPlanId } from 'geometry/geometry-model';
import { PVDocumentId } from 'infra-model/projektivelho/pv-model';
import { useNavigate } from 'react-router-dom';
import { PublicationId } from 'publication/publication-model';

export const appPath = {
    'frontpage': '/',
    'publication-search': '/publications',
    'publication-view': (id: PublicationId) => `/publications/${id}`,
    'inframodel-list': '/infra-model',
    'inframodel-upload': '/infra-model/upload',
    'inframodel-edit': (id: GeometryPlanId) => `/infra-model/edit/${id}`,
    'inframodel-import': (id: PVDocumentId) => `/infra-model/import/${id}`,
    'inframodel-plans': '/infra-model/plans',
    'inframodel-waiting': '/infra-model/waiting-for-approval',
    'inframodel-rejected': '/infra-model/rejected',
} as const;

export type AppNavigateFunction = <K extends keyof typeof appPath>(
    key: K,
    ...args: (typeof appPath)[K] extends (...args: unknown[]) => string
        ? Parameters<(typeof appPath)[K]>
        : []
) => void;

/**
 * @example
 * const navigate = useAppNavigate();
 * navigate('inframodel-list')
 * // or
 * navigate('inframodel-edit', someGeometryPlan.id)
 */
export function useAppNavigate(): AppNavigateFunction {
    const navigate = useNavigate();
    return (key, ...rest) => {
        const v = appPath[key];
        navigate(
            typeof v === 'string'
                ? v
                : // @ts-expect-error TS doesn't know this is a tuple
                  v(...rest),
        );
    };
}
