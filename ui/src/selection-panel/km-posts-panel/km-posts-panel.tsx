import * as React from 'react';
import { LayoutKmPost, LayoutKmPostId } from 'track-layout/track-layout-model';
import { KmPostBadge, KmPostBadgeStatus } from 'geoviite-design-lib/km-post/km-post-badge';
import styles from './km-posts-panel.scss';
import { useTranslation } from 'react-i18next';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { LayoutContext } from 'common/common-model';

type KmPostsPanelProps = {
    kmPosts: LayoutKmPost[];
    layoutContext: LayoutContext;
    onToggleKmPostSelection: (kmPost: LayoutKmPost) => void;
    selectedKmPosts?: LayoutKmPostId[];
    max?: number;
    disabled: boolean;
};

export const KmPostsPanel: React.FC<KmPostsPanelProps> = ({
    kmPosts,
    layoutContext,
    onToggleKmPostSelection,
    selectedKmPosts,
    max = 16,
    disabled,
}: KmPostsPanelProps) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbers(layoutContext);

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
                        (selectedPost) => selectedPost === kmPost.id,
                    );
                    const status = () => {
                        if (disabled) return KmPostBadgeStatus.DISABLED;
                        else if (isSelected) return KmPostBadgeStatus.SELECTED;
                        else return undefined;
                    };
                    return (
                        <li key={kmPost.id}>
                            <KmPostBadge
                                kmPost={kmPost}
                                onClick={() => onToggleKmPostSelection(kmPost)}
                                status={status()}
                                trackNumber={trackNumbers?.find(
                                    (tn) => tn.id === kmPost.trackNumberId,
                                )}
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
