import { GeometryPlanId } from 'geometry/geometry-model';
import { useNavigate } from 'react-router-dom';

const appPath = {
    'inframodel-list': '/infra-model',
    'inframodel-upload': '/infra-model/upload',
    'inframodel-edit': (id: GeometryPlanId) => `/infra-model/edit/${id}`,
    'inframodel-plans': '/infra-model/plans',
    'inframodel-waiting': '/infra-model/waiting-for-approval',
    'inframodel-rejected': '/infra-model/rejected',
} as const;

type AppNavigateFunction = <K extends keyof typeof appPath>(
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
