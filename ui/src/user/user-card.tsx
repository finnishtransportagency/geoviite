import * as React from 'react';
import { User } from 'user/user-model';
import Card from 'geoviite-design-lib/card/card';
import styles from './user-card.scss';
import { useTranslation } from 'react-i18next';
import { ShowMoreButton } from 'show-more-button/show-more-button';

type UserCardProps = {
    user: User;
};

export const UserCard: React.FC<UserCardProps> = ({ user }: UserCardProps) => {
    const { t } = useTranslation();
    const [showMoreDetails, setShowMoreDetails] = React.useState(false);

    return (
        <Card
            className={styles['user-card']}
            content={
                <React.Fragment>
                    <h3 className={styles['user-card__username-title']}>
                        {t('user-card.my-details')}
                    </h3>
                    <div className={styles['user-card__username']}>{user.details.userName}</div>

                    <div className={styles['user-card__show-more']}>
                        <ShowMoreButton
                            onShowMore={() => setShowMoreDetails(!showMoreDetails)}
                            expanded={showMoreDetails}
                        />
                    </div>

                    {showMoreDetails && (
                        <React.Fragment>
                            <section>
                                <h3 className={styles['user-card__role-title']}>
                                    {t('user-card.role')}
                                </h3>
                                <span>{user.role.name}</span>
                            </section>
                            <section>
                                <h3 className={styles['user-card__permissions-title']}>
                                    {t('user-card.permissions')}
                                </h3>
                                <span>{user.role.privileges.map((p) => p.name).join(', ')}</span>
                            </section>
                        </React.Fragment>
                    )}
                </React.Fragment>
            }></Card>
    );
};
