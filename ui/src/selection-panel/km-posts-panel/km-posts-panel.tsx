import * as React from 'react';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import { KmPostBadge, KmPostBadgeStatus } from 'geoviite-design-lib/km-post/km-post-badge';
import styles from './km-posts-panel.scss';
import { useTranslation } from 'react-i18next';

type KmPostsPanelProps = {
    kmPosts: LayoutKmPost[];
    onToggleKmPostSelection: (kmPost: LayoutKmPost) => void;
    selectedKmPosts?: LayoutKmPost[];
    max?: number;
};

export const KmPostsPanel: React.FC<KmPostsPanelProps> = ({
    kmPosts,
    onToggleKmPostSelection,
    selectedKmPosts,
    max = 18,
}: KmPostsPanelProps) => {
    const { t } = useTranslation();

    const [kmPostsCount, setKmPostsCount] = React.useState(0);
    const [visibleKmPosts, setVisibleKmPosts] = React.useState([] as LayoutKmPost[]);
    React.useEffect(() => {
        if (kmPosts) {
            const sortedPosts = [...kmPosts].sort(
                (a, b) => parseInt(a.kmNumber) - parseInt(b.kmNumber),
            );

            setVisibleKmPosts(sortedPosts.length < max + 1 ? sortedPosts : []);
            setKmPostsCount(sortedPosts.length);
        } else {
            setVisibleKmPosts([]);
            setKmPostsCount(0);
        }
    }, [kmPosts]);

    return (
        <div>
            <ol className={styles['km-posts-panel__km-posts']}>
                {visibleKmPosts.map((kmPost) => {
                    const isSelected = selectedKmPosts?.some(
                        (selectedPost) => selectedPost.id == kmPost.id,
                    );
                    return (
                        <li key={kmPost.id}>
                            <KmPostBadge
                                kmPost={kmPost}
                                onClick={() => onToggleKmPostSelection(kmPost)}
                                status={isSelected ? KmPostBadgeStatus.SELECTED : undefined}
                            />
                        </li>
                    );
                })}
            </ol>
            {kmPostsCount > max && (
                <span className={styles['km-posts-panel__subtitle']}>{`${t(
                    'selection-panel.zoom-closer',
                )}`}</span>
            )}

            {kmPostsCount === 0 && (
                <span className={styles['km-posts-panel__subtitle']}>{`${t(
                    'selection-panel.no-results',
                )}`}</span>
            )}
        </div>
    );
};
